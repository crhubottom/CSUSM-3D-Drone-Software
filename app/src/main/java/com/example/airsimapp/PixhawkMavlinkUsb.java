package com.example.airsimapp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.ActuatorOutputStatus;
import io.dronefleet.mavlink.common.Attitude;
import io.dronefleet.mavlink.common.CommandAck;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavResult;
import io.dronefleet.mavlink.common.RcChannelsOverride;
import io.dronefleet.mavlink.common.RequestDataStream;
import io.dronefleet.mavlink.common.ServoOutputRaw;
import io.dronefleet.mavlink.common.Statustext;
import io.dronefleet.mavlink.common.VfrHud;
import io.dronefleet.mavlink.minimal.Heartbeat;

public class PixhawkMavlinkUsb {

    private static final String TAG = "PixhawkMavlinkUsb";
    private static final String ACTION_USB_PERMISSION = "com.example.airsimapp.USB_PERMISSION";
    private volatile long lastLogTs = 0;
    private final Context context;

    private final AtomicInteger roll     = new AtomicInteger(0);
    private final AtomicInteger pitch    = new AtomicInteger(0);
    private final AtomicInteger yaw      = new AtomicInteger(0);
    private final AtomicInteger throttle = new AtomicInteger(0);

    private final AtomicBoolean armed          = new AtomicBoolean(false);
    private final AtomicBoolean lastArmRequest = new AtomicBoolean(false);
    private final AtomicBoolean pendingArm     = new AtomicBoolean(false);
    private final AtomicBoolean hasPendingArm  = new AtomicBoolean(false);

    private volatile UsbSerialPort       serialPort;
    private volatile UsbDeviceConnection usbConnection;
    private volatile MavlinkConnection   mav;

    // Pipe to give MAVLink a "real" blocking InputStream
    private volatile PipedInputStream  pipeIn;
    private volatile PipedOutputStream pipeOut;

    // Single thread that pumps USB reads -> pipeOut (ONLY reader of serialPort.read)
    private Thread usbPumpThread;
    private final AtomicBoolean usbPumpRunning = new AtomicBoolean(false);

    private ScheduledExecutorService sendLoop;
    private Thread recvThread;

    private volatile int targetSys  = -1;
    private volatile int targetComp = -1;

    private final int mySysId  = 255;
    private final int myCompId = 0;

    private volatile boolean connected = false;

    private final AtomicBoolean receiverRunning = new AtomicBoolean(false);

    public PixhawkMavlinkUsb(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setRoll(int v)     { roll.set(clamp(v, -1000, 1000)); }
    public void setPitch(int v)    { pitch.set(clamp(v, -1000, 1000)); }
    public void setYaw(int v)      { yaw.set(clamp(v, -1000, 1000)); }
    public void setThrottle(int v) { throttle.set(clamp(v, 0, 1000)); }
    // ---- Telemetry state (updated by receiver thread) ----
    private volatile float altitudeM = 0f;      // meters
    private volatile float headingDeg = 0f;     // degrees 0..359
    private volatile float groundSpeedMs = 0f;  // m/s

    // Motors as percent 0..100 (quad). Updated from SERVO_OUTPUT_RAW.
    private final AtomicInteger m1Pct = new AtomicInteger(0);
    private final AtomicInteger m2Pct = new AtomicInteger(0);
    private final AtomicInteger m3Pct = new AtomicInteger(0);
    private final AtomicInteger m4Pct = new AtomicInteger(0);
    public float getAltitude() { return altitudeM; }
    public float getHeading()  { return headingDeg; }
    public float getGroundSpeed() { return groundSpeedMs; }


    // MAVLink msg IDs
    private static final int MSG_ID_VFR_HUD = 74;
    private static final int MSG_ID_SERVO_OUTPUT_RAW = 36;
    private static final int MSG_ID_ACTUATOR_OUTPUT_STATUS = 375;
    private static final int MSG_ID_ATTITUDE = 30;


    //tells Pixhawk to send messages to drone phone at specified interval
    private void requestMessageInterval(int messageId, int hz) {
        if (mav == null || targetSys < 0) return;

        int comp = (targetComp > 0) ? targetComp : 1;
        float intervalUs = 1_000_000f / hz;

        try {
            CommandLong cmd = CommandLong.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL)
                    .confirmation(0)
                    .param1(messageId)
                    .param2(intervalUs)
                    .build();

            mav.send2(mySysId, myCompId, cmd);
            Log.i(TAG, "Requested msg " + messageId + " at " + hz + " Hz");
        } catch (Exception e) {
            Log.e(TAG, "requestMessageInterval failed for msg " + messageId, e);
        }
    }

    // Call after heartbeat targetSys known
    //tells pixhawk which messages to send
    private void requestDataStreamsLegacy() {
        if (mav == null || targetSys < 0) return;
        int comp = (targetComp > 0) ? targetComp : 1;

        try {
            // RAW_SENSORS
            mav.send2(mySysId, myCompId, RequestDataStream.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .reqStreamId(2)
                    .reqMessageRate(5)
                    .startStop(1)
                    .build());

            // RC_CHANNELS
            mav.send2(mySysId, myCompId, RequestDataStream.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .reqStreamId(3)
                    .reqMessageRate(10)
                    .startStop(1)
                    .build());

            // EXTRA1
            mav.send2(mySysId, myCompId, RequestDataStream.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .reqStreamId(10)
                    .reqMessageRate(10)
                    .startStop(1)
                    .build());

            // EXTRA2
            mav.send2(mySysId, myCompId, RequestDataStream.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .reqStreamId(11)
                    .reqMessageRate(10)
                    .startStop(1)
                    .build());

            // EXTRA3
            mav.send2(mySysId, myCompId, RequestDataStream.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .reqStreamId(12)
                    .reqMessageRate(10)
                    .startStop(1)
                    .build());

            Log.i(TAG, "Requested legacy data streams incl. RC_CHANNELS (3)");
        } catch (Exception e) {
            Log.e(TAG, "requestDataStreamsLegacy failed", e);
        }
    }



    /** Returns motor outputs as 0..100 percent (quad). Safe to call from UI thread. */
    public int[] getMotorOutputsPercent() {
        return new int[] { m1Pct.get(), m2Pct.get(), m3Pct.get(), m4Pct.get() };
    }


    public boolean isArmed()     { return armed.get(); }
    public boolean isConnected() { return connected; }
    // Convert PWM (typically 1000..2000) to 0..100%
    private static int pwmToPercent(int pwm) {
        if (pwm <= 0) return 0; // 0 can mean "not present" depending on firmware
        // clamp to expected range
        int clamped = clamp(pwm, 1000, 2000);
        return (clamped - 1000) / 10; // 1000->0, 2000->100
    }

    // If you want percent from throttle 0..1000. Not used currently
    public int getThrottlePercent() {
        return clamp(throttle.get() / 10, 0, 100);
    }
    public void arm(boolean arm) {      //not used
        if (arm) arm();
        else disarm();
    }
    //connect drone phone to Pixhawk via USBC-OTG connection
    public void connect() {
        Log.i(TAG, "connect() called");
        close();

        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        for (UsbDevice d : manager.getDeviceList().values()) {
            Log.i(TAG, "USB device: " + d.getDeviceName()
                    + " VID=" + d.getVendorId()
                    + " PID=" + d.getProductId());
        }

        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        Log.i(TAG, "drivers found: " + drivers.size());

        if (drivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!manager.hasPermission(device)) {
            requestPermission(manager, device);
            return;
        }

        usbConnection = manager.openDevice(device);
        if (usbConnection == null) {
            Log.e(TAG, "openDevice returned null");
            return;
        }

        List<UsbSerialPort> ports = driver.getPorts();
        if (ports == null || ports.isEmpty()) {
            Log.e(TAG, "Driver has no ports");
            return;
        }

        // Pixhawk USB MAVLink is normally 115200
        serialPort = ports.get(0);  //attempts connection on first serial port, assumes no other devices are connected to phone
        try {
            serialPort.open(usbConnection);
            serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // Helps some CDC devices
            try { serialPort.setDTR(true); } catch (Exception ignored) {}
            try { serialPort.setRTS(true); } catch (Exception ignored) {}

            // Pipe size: bigger than MAVLink frames so readFully() behaves well
            pipeOut = new PipedOutputStream();
            pipeIn  = new PipedInputStream(pipeOut, 8192);

            startUsbPump();

            // Output can write directly to serial
            OutputStream out = new UsbSerialOutputStream(serialPort);

            // MAVLink reads ONLY from pipeIn
            mav = MavlinkConnection.create(pipeIn, out);

            connected = true;
            Log.i(TAG, "USB opened; MAVLink created. Starting receiver + send loop...");

            startReceiverOnce();
            startSendLoop();

        } catch (Exception e) {
            Log.e(TAG, "USB connect failed", e);
            close();
        }
    }
        //disconnects Pixhawk from drone phone, resets variables
    public void close() {
        Log.i(TAG, "close() called");

        try { usbPumpRunning.set(false); } catch (Exception ignored) {}
        try { if (usbPumpThread != null) usbPumpThread.interrupt(); } catch (Exception ignored) {}

        if (sendLoop != null) { try { sendLoop.shutdownNow(); } catch (Exception ignored) {} sendLoop = null; }
        if (recvThread != null) { try { recvThread.interrupt(); } catch (Exception ignored) {} recvThread = null; }

        try { if (pipeIn  != null) pipeIn.close();  } catch (Exception ignored) {}
        try { if (pipeOut != null) pipeOut.close(); } catch (Exception ignored) {}

        try { if (serialPort    != null) serialPort.close();    } catch (Exception ignored) {}
        try { if (usbConnection != null) usbConnection.close(); } catch (Exception ignored) {}

        usbPumpThread = null;
        pipeIn = null;
        pipeOut = null;

        serialPort    = null;
        usbConnection = null;
        mav           = null;

        connected     = false;
        targetSys     = -1;
        targetComp    = -1;
        armed.set(false);
        throttle.set(0);
        altitudeM = 0f;
        headingDeg = 0f;
        groundSpeedMs = 0f;
        m1Pct.set(0); m2Pct.set(0); m3Pct.set(0); m4Pct.set(0);
        receiverRunning.set(false);
    }

    // ---------------- USB pump: only place that calls serialPort.read() ----------------

    private void startUsbPump() {
        if (usbPumpRunning.getAndSet(true)) return;

        usbPumpThread = new Thread(() -> {
            Log.i(TAG, "USB pump started");
            byte[] buf = new byte[512];

            while (usbPumpRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    UsbSerialPort p = serialPort;
                    PipedOutputStream out = pipeOut;
                    if (p == null || out == null) break;

                    int n = p.read(buf, 200);
                    if (n > 0) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                    // n==0 is just a timeout; loop again

                } catch (Exception e) {
                    Log.e(TAG, "USB pump error", e);
                    break;
                }
            }

            usbPumpRunning.set(false);
            Log.i(TAG, "USB pump stopped");
        }, "usb-pump");

        usbPumpThread.start();
    }

    // ---------------- ARM / DISARM ----------------

    public void arm() {
        throttle.set(0);
        lastArmRequest.set(true);

        MavlinkConnection local = mav;
        if (local == null) { Log.w(TAG, "arm(): not connected"); return; }

        if (targetSys < 0) {
            Log.w(TAG, "arm(): waiting for heartbeat, queueing ARM");
            pendingArm.set(true);
            hasPendingArm.set(true);
            return;
        }

        sendArmDisarm(true);
    }

    public void disarm() {
        throttle.set(0);
        lastArmRequest.set(false);

        if (mav == null) return;

        if (targetSys < 0) {
            Log.w(TAG, "disarm(): waiting for heartbeat, queueing DISARM");
            pendingArm.set(false);
            hasPendingArm.set(true);
            return;
        }

        sendArmDisarm(false);
        armed.set(false);
    }

    private void sendArmDisarm(boolean arm) {
        try {
            int comp = (targetComp > 0) ? targetComp : 1;

            CommandLong cmd = CommandLong.builder()
                    .targetSystem(targetSys)
                    .targetComponent(comp)
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .confirmation(0)
                    .param1(arm ? 1 : 0)
                    .build();

            mav.send2(mySysId, myCompId, cmd);
            Log.i(TAG, (arm ? "ARM" : "DISARM") + " sent to sys=" + targetSys + " comp=" + comp);
        } catch (Exception e) {
            Log.e(TAG, "sendArmDisarm failed", e);
        }
    }

    // ---------------- Receiver ----------------

    private void startReceiverOnce() {
        if (receiverRunning.getAndSet(true)) return;

        recvThread = new Thread(() -> {
            Log.i(TAG, "Receiver thread started");

            while (!Thread.currentThread().isInterrupted()) {
                MavlinkConnection localMav = mav;
                if (localMav == null) {
                    try { Thread.sleep(50); } catch (InterruptedException ie) { return; }
                    continue;
                }

                try {
                    MavlinkMessage<?> msg = localMav.next(); // blocks until full MAVLink frame
                    if (msg == null) continue;

                    Object p = msg.getPayload();

                    if (p instanceof Heartbeat) {

                        if (targetSys < 0) {
                            targetSys  = msg.getOriginSystemId();
                            targetComp = msg.getOriginComponentId();

                            requestDataStreamsLegacy(); //once heartbeat is detected, request data streams

                            requestMessageInterval(MSG_ID_VFR_HUD, 5);
                            requestMessageInterval(MSG_ID_SERVO_OUTPUT_RAW, 10);        //request more data streams on specified intervals
                            requestMessageInterval(MSG_ID_ACTUATOR_OUTPUT_STATUS, 10);
                            if (hasPendingArm.getAndSet(false)) {
                                boolean armReq = pendingArm.get();
                                Log.i(TAG, "Flushing queued " + (armReq ? "ARM" : "DISARM"));
                                sendArmDisarm(armReq);
                            }
                        }
                    } else if (p instanceof Statustext) {
                        //if theres an error
                       // Log.i(TAG, "STATUSTEXT: " + ((Statustext) p).text());

                    } else if (p instanceof CommandAck) {
                        //different type of error
                        CommandAck ack = (CommandAck) p;
                        Log.i(TAG, "ACK cmd=" + ack.command() + " result=" + ack.result());
                        if (ack.command().entry() == MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) {
                            boolean ok = ack.result().entry() == MavResult.MAV_RESULT_ACCEPTED;
                            boolean newState = ok && lastArmRequest.get();
                            armed.set(newState);
                            if (!newState) throttle.set(0);
                           // Log.i(TAG, "ARM=" + newState + " ack=" + ack.result());
                        } else {
                           // Log.i(TAG, "ACK: " + acvk.command() + " -> " + ack.result());
                        }
                    }else if (p instanceof VfrHud) {
                        //vfrhud is used for compass telemetry
                        VfrHud hud = (VfrHud) p;

                        headingDeg = normalize360((float) hud.heading());
                        groundSpeedMs = hud.groundspeed();
                        altitudeM = hud.alt();
                        Log.i(TAG, String.format("VFR_HUD heading=%.2f", headingDeg));
                    } else if (p instanceof Attitude) { //altimeter telemetry
                        Attitude a = (Attitude) p;
                        // ATTITUDE.yaw is radians
                        headingDeg = normalize360((float)Math.toDegrees(a.yaw()));
                        Log.i(TAG, String.format("ATTITUDE yawRad=%.6f -> heading=%.2f", a.yaw(), headingDeg));

                    }else if (p instanceof ServoOutputRaw) {    //first type of motor telemetry, rarely used
                        ServoOutputRaw s = (ServoOutputRaw) p;

                        int m1 = pwmToPercent(s.servo1Raw());
                        int m2 = pwmToPercent(s.servo2Raw());
                        int m3 = pwmToPercent(s.servo3Raw());
                        int m4 = pwmToPercent(s.servo4Raw());

                        m1Pct.set(m1);
                        m2Pct.set(m2);
                        m3Pct.set(m3);
                        m4Pct.set(m4);

                        Log.i(TAG, "SERVO_OUTPUT_RAW pwm="
                                + s.servo1Raw() + ","
                                + s.servo2Raw() + ","
                                + s.servo3Raw() + ","
                                + s.servo4Raw()
                                + " pct=" + m1 + "," + m2 + "," + m3 + "," + m4);
                    } else if (p instanceof ActuatorOutputStatus) { //second type of motor telemetry, more commonly supported. Is what our Pixhawk sends
                    ActuatorOutputStatus a = (ActuatorOutputStatus) p;

                    int m1 = Math.round((float) a.actuator().get(0) * 100f);
                    int m2 = Math.round((float) a.actuator().get(1) * 100f);
                    int m3 = Math.round((float) a.actuator().get(2) * 100f);
                    int m4 = Math.round((float) a.actuator().get(3) * 100f);

                    m1Pct.set(clamp(m1, 0, 100));
                    m2Pct.set(clamp(m2, 0, 100));
                    m3Pct.set(clamp(m3, 0, 100));
                    m4Pct.set(clamp(m4, 0, 100));
                }

                } catch (java.io.EOFException eof) {
                    Log.e(TAG, "Serial EOF — device disconnected?", eof);
                    close();
                    return;
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) Log.e(TAG, "Receiver error", e);
                }
            }
        }, "mav-recv");

        recvThread.start();
    }

        //normalizes heading
    private static float normalize360(float deg) {
        // returns value in [0,360)
        float r = deg % 360f;
        if (r < 0) r += 360f;
        return r;
    }
    // ---------------- 20Hz RC override ----------------


        //sends inputs to pixhawk on 20hz loop
    private void startSendLoop() {
        if (sendLoop != null) return;

        sendLoop = Executors.newSingleThreadScheduledExecutor();
        sendLoop.scheduleWithFixedDelay(() -> {
            try {

                MavlinkConnection localMav = mav;
                if (localMav == null || targetSys < 0) return;

                int comp = (targetComp > 0) ? targetComp : 1;
                int thr  = armed.get() ? throttle.get() : 0;

                RcChannelsOverride rc = RcChannelsOverride.builder()    //rcchannelsoverride is the main way to manually control pixhawk
                        .targetSystem(targetSys)
                        .targetComponent(comp)
                        .chan1Raw(axisToPwm(roll.get()))
                        .chan2Raw(axisToPwm(pitch.get()))
                        .chan3Raw(throttleToPwm(thr))
                        .chan4Raw(axisToPwm(yaw.get()))
                        // 65535 = ignore channel (per MAVLink spec for RC override)
                        .chan5Raw(65535).chan6Raw(65535).chan7Raw(65535).chan8Raw(65535)
                        .build();
                int ch1 = axisToPwm(roll.get());
                int ch2 = axisToPwm(pitch.get());
                int ch3 = throttleToPwm(thr);
                int ch4 = axisToPwm(yaw.get());

                if ((System.currentTimeMillis() / 500) != (lastLogTs / 500)) { // ~2 Hz
                    lastLogTs = System.currentTimeMillis();
                    Log.i(TAG, "RC OVERRIDE pwm: ch1=" + ch1 + " ch2=" + ch2 + " ch3=" + ch3 + " ch4=" + ch4
                            + " armed=" + armed.get() + " thrRaw=" + throttle.get());
                }
                localMav.send2(mySysId, myCompId, rc);

            } catch (Exception e) {
                Log.e(TAG, "RC send error", e);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    // ---------------- Helpers ----------------

    private static int axisToPwm(int v) {
        return 1500 + (clamp(v, -1000, 1000) * 500) / 1000;
    }

    private static int throttleToPwm(int z) {
        return 1000 + clamp(z, 0, 1000);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void requestPermission(UsbManager manager, UsbDevice device) {
        // Some Android versions require MUTABLE so the platform can attach EXTRA_PERMISSION_GRANTED.
        int piFlags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE
                : 0;

        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), piFlags
        );

        ContextCompat.registerReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                try { context.unregisterReceiver(this); } catch (Exception ignored) {}

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    connect();
                } else {
                    Log.e(TAG, "USB permission denied");
                }
            }
        }, new IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED);

        manager.requestPermission(device, pi);
    }

    // ---------------- Output wrapper ----------------

    static class UsbSerialOutputStream extends OutputStream {
        private final UsbSerialPort port;

        UsbSerialOutputStream(UsbSerialPort port) { this.port = port; }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                byte[] slice = (off == 0 && len == b.length) ? b : Arrays.copyOfRange(b, off, off + len);
                port.write(slice, 200);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }
    }
}