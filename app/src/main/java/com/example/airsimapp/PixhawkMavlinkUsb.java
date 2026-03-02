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
import io.dronefleet.mavlink.common.ManualControl;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavResult;
import io.dronefleet.mavlink.common.Statustext;
import io.dronefleet.mavlink.minimal.Heartbeat;

public class PixhawkMavlinkUsb {

    public interface Listener {
        void onStatusText(String text);
        void onArmStateChanged(boolean isArmed);
    }

    private static final String TAG = "PixhawkMavlinkUsb";
    private static final String ACTION_USB_PERMISSION = "com.example.airsimapp.USB_PERMISSION";

    private final Context context;
    private final Listener listener;

    private final AtomicInteger rollY, pitchX, yawR, thrZ;
    private final AtomicBoolean armed;

    private UsbSerialPort serialPort;
    private UsbDeviceConnection usbConn;
    private MavlinkConnection mav;

    private ScheduledExecutorService sendLoop;
    private Thread recvThread;

    private volatile int targetSysId = -1;
    private volatile int targetCompId = -1;

    private final int mySysId = 255;
    private final int myCompId = 0;

    private volatile boolean connected = false;

    public PixhawkMavlinkUsb(
            Context context,
            AtomicInteger rollY,
            AtomicInteger pitchX,
            AtomicInteger yawR,
            AtomicInteger thrZ,
            AtomicBoolean armed,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.rollY = rollY;
        this.pitchX = pitchX;
        this.yawR = yawR;
        this.thrZ = thrZ;
        this.armed = armed;
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public void connect() {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers.isEmpty()) {
            Log.e(TAG, "No USB serial devices found");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(usbManager, device);
            Log.i(TAG, "Requested USB permission");
            return;
        }

        try {
            openDriver(usbManager, driver);
            startReceiverThread();
            startSendLoop();
            connected = true;
            Log.i(TAG, "USB connected, waiting for HEARTBEAT...");
        } catch (Exception e) {
            Log.e(TAG, "Connect failed", e);
            close();
        }
    }

    private void requestUsbPermission(UsbManager usbManager, UsbDevice device) {
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
        );

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ContextCompat.registerReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    context.unregisterReceiver(this);
                    // Try connect again after user responds
                    connect();
                }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        usbManager.requestPermission(device, pi);
    }

    private void openDriver(UsbManager usbManager, UsbSerialDriver driver) throws IOException {
        usbConn = usbManager.openDevice(driver.getDevice());
        if (usbConn == null) throw new IOException("openDevice returned null (permission?)");

        serialPort = driver.getPorts().get(0);
        serialPort.open(usbConn);
        serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        InputStream in = new UsbSerialInputStream(serialPort);
        OutputStream out = new UsbSerialOutputStream(serialPort);

        mav = MavlinkConnection.create(in, out);
    }

    public void arm() {
        if (mav == null || targetSysId < 0) return;

        // Ensure low throttle before arming
        thrZ.set(0);
        armed.set(false);

        try {
            CommandLong arm = CommandLong.builder()
                    .targetSystem(targetSysId)
                    .targetComponent(targetCompId > 0 ? targetCompId : 1)
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .confirmation(0)
                    .param1(1)
                    .build();

            mav.send2(mySysId, myCompId, arm);
        } catch (Exception e) {
            Log.e(TAG, "arm() failed", e);
        }
    }

    public void disarm() {
        if (mav == null || targetSysId < 0) return;

        thrZ.set(0);
        armed.set(false);
        if (listener != null) listener.onArmStateChanged(false);

        try {
            CommandLong disarm = CommandLong.builder()
                    .targetSystem(targetSysId)
                    .targetComponent(targetCompId > 0 ? targetCompId : 1)
                    .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                    .confirmation(0)
                    .param1(0)
                    .build();

            mav.send2(mySysId, myCompId, disarm);
        } catch (Exception e) {
            Log.e(TAG, "disarm() failed", e);
        }
    }

    public void close() {
        try { if (sendLoop != null) sendLoop.shutdownNow(); } catch (Exception ignored) {}
        try { if (serialPort != null) serialPort.close(); } catch (Exception ignored) {}
        try { if (usbConn != null) usbConn.close(); } catch (Exception ignored) {}

        sendLoop = null;
        serialPort = null;
        usbConn = null;
        mav = null;

        connected = false;
        targetSysId = -1;
        targetCompId = -1;

        armed.set(false);
        thrZ.set(0);
    }

    private void startReceiverThread() {
        recvThread = new Thread(() -> {
            try {
                while (mav != null) {
                    MavlinkMessage<?> msg = mav.next();
                    Object p = msg.getPayload();

                    if (p instanceof Heartbeat) {
                        Heartbeat hb = (Heartbeat) p;
                        if (targetSysId < 0) {
                            targetSysId = msg.getOriginSystemId();
                            targetCompId = msg.getOriginComponentId();
                            Log.i(TAG, "HEARTBEAT sys=" + targetSysId + " comp=" + targetCompId +
                                    " autopilot=" + hb.autopilot() + " type=" + hb.type());
                        }

                    } else if (p instanceof Statustext) {
                        Statustext st = (Statustext) p;
                        if (listener != null) listener.onStatusText(st.text());

                    } else if (p instanceof CommandAck) {
                        CommandAck ack = (CommandAck) p;
                        Log.i(TAG, "COMMAND_ACK cmd=" + ack.command() + " result=" + ack.result());

                        if (ack.command().entry() == MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) {
                            boolean ok = (ack.result().entry() == MavResult.MAV_RESULT_ACCEPTED);
                            armed.set(ok);
                            if (!ok) thrZ.set(0);
                            if (listener != null) listener.onArmStateChanged(ok);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Receiver thread ended", e);
            }
        }, "mav-recv");

        recvThread.start();
    }

    private void startSendLoop() {
        sendLoop = Executors.newSingleThreadScheduledExecutor();
        sendLoop.scheduleWithFixedDelay(() -> {
            try {
                if (mav == null || targetSysId < 0) return;

                // Gate throttle until armed (extra safety)
                int z = armed.get() ? thrZ.get() : 0;

                ManualControl mc = ManualControl.builder()
                        .target(targetSysId)
                        .x(pitchX.get())
                        .y(rollY.get())
                        .z(z)
                        .r(yawR.get())
                        .buttons(0)
                        .build();

                mav.send2(mySysId, myCompId, mc);
            } catch (Exception e) {
                Log.e(TAG, "Send loop error", e);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    // --- Stream wrappers ---

    static class UsbSerialInputStream extends InputStream {
        private final UsbSerialPort port;
        private final byte[] one = new byte[1];
        UsbSerialInputStream(UsbSerialPort port) { this.port = port; }

        @Override public int read() throws IOException {
            int n = read(one, 0, 1);
            return (n <= 0) ? -1 : (one[0] & 0xFF);
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            try {
                int n = port.read(b, 200);
                if (n <= 0) return 0;
                if (off != 0) System.arraycopy(b, 0, b, off, n);
                return n;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    static class UsbSerialOutputStream extends OutputStream {
        private final UsbSerialPort port;
        UsbSerialOutputStream(UsbSerialPort port) { this.port = port; }

        @Override public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            try {
                byte[] slice = (off == 0 && len == b.length) ? b : Arrays.copyOfRange(b, off, off + len);
                port.write(slice, 200);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}