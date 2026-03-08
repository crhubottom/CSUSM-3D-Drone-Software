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
import java.io.InputStream;
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
import io.dronefleet.mavlink.common.CommandAck;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavResult;
import io.dronefleet.mavlink.common.RcChannelsOverride;
import io.dronefleet.mavlink.common.Statustext;
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

    public boolean isArmed()     { return armed.get(); }
    public boolean isConnected() { return connected; }

    public void arm(boolean arm) {
        if (arm) arm();
        else disarm();
    }

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

        // Pixhawk USB MAVLink is normally 115200. Avoid baud probing (it causes resets / parsing issues).
        serialPort = ports.get(0);
        try {
            serialPort.open(usbConnection);
            serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // Helps some CDC devices (safe if unsupported)
            try { serialPort.setDTR(true); } catch (Exception ignored) {}
            try { serialPort.setRTS(true); } catch (Exception ignored) {}

            // Pipe size: bigger than MAVLink frames so readFully() behaves well
            pipeOut = new PipedOutputStream();
            pipeIn  = new PipedInputStream(pipeOut, 8192);

            startUsbPump();

            // Output can write directly to serial
            OutputStream out = new UsbSerialOutputStream(serialPort);

            // IMPORTANT: MAVLink reads ONLY from pipeIn now (not serialPort.read directly)
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
            long msgCount = 0;

            while (!Thread.currentThread().isInterrupted()) {
                MavlinkConnection localMav = mav;
                if (localMav == null) {
                    try { Thread.sleep(50); } catch (InterruptedException ie) { return; }
                    continue;
                }

                try {
                    MavlinkMessage<?> msg = localMav.next(); // blocks until full MAVLink frame
                    if (msg == null) continue;

                    if (++msgCount % 25 == 0) Log.i(TAG, "MAVLink packets: " + msgCount);

                    Object p = msg.getPayload();

                    if (p instanceof Heartbeat) {
                        if (targetSys < 0) {
                            targetSys  = msg.getOriginSystemId();
                            targetComp = msg.getOriginComponentId();
                            Log.i(TAG, "HEARTBEAT sys=" + targetSys + " comp=" + targetComp);

                            if (hasPendingArm.getAndSet(false)) {
                                boolean armReq = pendingArm.get();
                                Log.i(TAG, "Flushing queued " + (armReq ? "ARM" : "DISARM"));
                                sendArmDisarm(armReq);
                            }
                        }

                    } else if (p instanceof Statustext) {
                        Log.i(TAG, "STATUSTEXT: " + ((Statustext) p).text());

                    } else if (p instanceof CommandAck) {
                        CommandAck ack = (CommandAck) p;

                        if (ack.command().entry() == MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) {
                            boolean ok = ack.result().entry() == MavResult.MAV_RESULT_ACCEPTED;
                            boolean newState = ok && lastArmRequest.get();
                            armed.set(newState);
                            if (!newState) throttle.set(0);
                            Log.i(TAG, "ARM=" + newState + " ack=" + ack.result());
                        } else {
                            Log.i(TAG, "ACK: " + ack.command() + " -> " + ack.result());
                        }
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

    // ---------------- 20Hz RC override ----------------

    private void startSendLoop() {
        if (sendLoop != null) return;

        sendLoop = Executors.newSingleThreadScheduledExecutor();
        sendLoop.scheduleWithFixedDelay(() -> {
            try {

                MavlinkConnection localMav = mav;
                if (localMav == null || targetSys < 0) return;

                int comp = (targetComp > 0) ? targetComp : 1;
                int thr  = armed.get() ? throttle.get() : 0;

                RcChannelsOverride rc = RcChannelsOverride.builder()
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