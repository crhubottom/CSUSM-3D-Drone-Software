import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.view.View;

import com.example.airsimapp.JoystickView;
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
import io.dronefleet.mavlink.common.*;
import io.dronefleet.mavlink.minimal.Heartbeat;

public class PixhawkUsbJoystickController {

    private static final String TAG = "PixhawkUsb";

    // MAVLink
    private MavlinkConnection mav;
    private volatile int targetSysId = -1;
    private volatile int targetCompId = -1;

    private final int mySysId = 255; // GCS id
    private final int myCompId = 0;

    // USB serial
    private UsbSerialPort serialPort;
    private UsbDeviceConnection usbConn;

    // Joystick state (MANUAL_CONTROL fields)
    private final AtomicInteger rollY  = new AtomicInteger(0);   // MANUAL_CONTROL.y
    private final AtomicInteger pitchX = new AtomicInteger(0);   // MANUAL_CONTROL.x
    private final AtomicInteger yawR   = new AtomicInteger(0);   // MANUAL_CONTROL.r
    private final AtomicInteger thrZ   = new AtomicInteger(0);   // MANUAL_CONTROL.z (0..1000)

    // Safety gating
    private final AtomicBoolean armed = new AtomicBoolean(false);

    private ScheduledExecutorService sendLoop;
    private Thread recvThread;

    // ---------- PUBLIC ENTRY POINTS ----------

    /** Call this once you have the Fragment root view (or Activity view). */
    public void setupJoysticks(View rootView, int joystick1Id, int joystick2Id) {
        JoystickView joystick = rootView.findViewById(joystick1Id);
        JoystickView joystick2 = rootView.findViewById(joystick2Id);

        // Left stick: yaw + throttle
        joystick.setJoystickListener((angle, strength) -> {
            if (strength < 5) strength = 0; // deadzone

            int[] xy = polarToXY(angle, strength); // your angle system
            int x = xy[0]; // left/right
            int y = xy[1]; // up/down

            yawR.set(x);

            int throttle = stickYToThrottle(y);
            thrZ.set(armed.get() ? throttle : 0); // IMPORTANT: throttle gated until armed

            Log.d(TAG, "L stick angle=" + angle + " str=" + strength + " xy=" + Arrays.toString(xy)
                    + " yaw=" + yawR.get() + " thr=" + thrZ.get());
        });

        // Right stick: roll + pitch
        joystick2.setJoystickListener((angle, strength) -> {
            if (strength < 5) strength = 0; // deadzone

            int[] xy = polarToXY(angle, strength);
            int x = xy[0];
            int y = xy[1];

            rollY.set(x);
            pitchX.set(y);

            Log.d(TAG, "R stick angle=" + angle + " str=" + strength + " xy=" + Arrays.toString(xy)
                    + " roll=" + rollY.get() + " pitch=" + pitchX.get());
        });
    }

    /**
     * Connect to Pixhawk over USB serial.
     * You must already have USB permission granted for the device.
     */
    public void connectUsb(Context context) throws IOException {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers.isEmpty()) throw new IOException("No USB serial devices found. Is Pixhawk connected via OTG?");

        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();

        // Ensure permission is granted elsewhere (recommended), otherwise openDevice returns null.
        usbConn = usbManager.openDevice(device);
        if (usbConn == null) {
            throw new IOException("USB permission not granted (openDevice returned null).");
        }

        serialPort = driver.getPorts().get(0);
        serialPort.open(usbConn);
        serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        // Wrap UsbSerialPort in streams so dronefleet MavlinkConnection can use it.
        InputStream in = new UsbSerialInputStream(serialPort);
        OutputStream out = new UsbSerialOutputStream(serialPort);

        mav = MavlinkConnection.create(in, out);

        startReceiverThread();
        startSendLoop();

        Log.i(TAG, "USB connected. Waiting for HEARTBEAT...");
    }

    /** Send ARM command. (Throttle is gated to 0 until arm accepted.) */
    public void arm() throws IOException {
        if (mav == null) throw new IOException("Not connected");
        if (targetSysId < 0) throw new IOException("No heartbeat yet");

        // Make absolutely sure we present "throttle low" before arming
        thrZ.set(0);

        CommandLong arm = CommandLong.builder()
                .targetSystem(targetSysId)
                .targetComponent(targetCompId > 0 ? targetCompId : 1)
                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                .confirmation(0)
                .param1(1) // 1 = arm
                .build();

        mav.send2(mySysId, myCompId, arm);
        Log.i(TAG, "ARM command sent");
    }

    /** Send DISARM command and force throttle to 0. */
    public void disarm() throws IOException {
        if (mav == null) throw new IOException("Not connected");
        if (targetSysId < 0) throw new IOException("No heartbeat yet");

        thrZ.set(0);
        armed.set(false);

        CommandLong disarm = CommandLong.builder()
                .targetSystem(targetSysId)
                .targetComponent(targetCompId > 0 ? targetCompId : 1)
                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
                .confirmation(0)
                .param1(0) // 0 = disarm
                .build();

        mav.send2(mySysId, myCompId, disarm);
        Log.i(TAG, "DISARM command sent");
    }

    /** Clean shutdown */
    public void close() {
        try {
            if (sendLoop != null) sendLoop.shutdownNow();
            if (serialPort != null) serialPort.close();
            if (usbConn != null) usbConn.close();
        } catch (Exception ignored) {}
        armed.set(false);
        thrZ.set(0);
        Log.i(TAG, "Closed");
    }

    // ---------- MAVLINK THREADS ----------

    private void startReceiverThread() {
        recvThread = new Thread(() -> {
            try {
                while (true) {
                    MavlinkMessage<?> msg = mav.next();
                    Object p = msg.getPayload();

                    if (p instanceof Heartbeat) {
                        Heartbeat hb = (Heartbeat) p;
                        if (targetSysId < 0) {
                            targetSysId = msg.getOriginSystemId();
                            targetCompId = msg.getOriginComponentId();
                            Log.i(TAG, "HEARTBEAT from sys=" + targetSysId + " comp=" + targetCompId
                                    + " autopilot=" + hb.autopilot()
                                    + " type=" + hb.type()
                                    + " baseMode=" + hb.baseMode()
                                    + " systemStatus=" + hb.systemStatus());
                        }
                    } else if (p instanceof Statustext) {
                        Statustext st = (Statustext) p;
                        Log.i(TAG, "STATUSTEXT: " + st.text());

                    } else if (p instanceof CommandAck) {
                        CommandAck ack = (CommandAck) p;
                        Log.i(TAG, "COMMAND_ACK: cmd=" + ack.command() + " result=" + ack.result());


                        // Arm accepted?
                        if (ack.command().entry() == MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) {
                            if (ack.result().entry() == MavResult.MAV_RESULT_ACCEPTED) {
                                armed.set(true);
                                Log.i(TAG, "ARM accepted -> throttle now enabled");
                            } else {
                                armed.set(false);
                                thrZ.set(0);
                                Log.w(TAG, "ARM rejected -> throttle forced to 0");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Receiver thread died", e);
            }
        }, "mav-recv");

        recvThread.start();
    }

    private void startSendLoop() {
        sendLoop = Executors.newSingleThreadScheduledExecutor();
        sendLoop.scheduleAtFixedRate(() -> {
            try {
                if (mav == null || targetSysId < 0) return;

                ManualControl mc = ManualControl.builder()
                        .target(targetSysId)
                        .x(pitchX.get())
                        .y(rollY.get())
                        .z(thrZ.get())   // 0..1000 (gated until armed)
                        .r(yawR.get())
                        .buttons(0)
                        .build();

                mav.send2(mySysId, myCompId, mc);
            } catch (Exception e) {
                Log.e(TAG, "Send loop error", e);
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // 20 Hz
    }

    // ---------- JOYSTICK MATH ----------

    /**
     * Your joystick coordinate system:
     * 0° = right
     * -90° ≈ up
     * +90° ≈ down
     * +/-180° = left
     *
     * returns {x,y} in [-1000..1000], where y>0 is up.
     */
    static int[] polarToXY(double angleDeg, double strength) {
        strength = clampDouble(strength, 0, 100);
        double mag = strength / 100.0;          // 0..1

        // NEGATE because your angles go clockwise (0 right, negative up)
        double rad = Math.toRadians(-angleDeg);

        double x = Math.cos(rad) * mag; // right/left
        double y = Math.sin(rad) * mag; // up/down

        int xi = (int) Math.round(x * 1000.0);
        int yi = (int) Math.round(y * 1000.0);

        return new int[]{ clampInt(xi, -1000, 1000), clampInt(yi, -1000, 1000) };
    }

    static int stickYToThrottle(int y /* -1000..1000 */) {
        // y=-1000 (down) => 0
        // y=+1000 (up)   => 1000
        return clampInt((y + 1000) / 2, 0, 1000);
    }

    static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static double clampDouble(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- STREAM WRAPPERS (UsbSerialPort -> InputStream/OutputStream) ----------

    /** Blocking-ish InputStream wrapper around UsbSerialPort.read(...) */
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
                int n = port.read(b, 200); // ms timeout
                if (n <= 0) return 0;
                // UsbSerialPort.read fills from index 0; if off != 0, shift
                if (off != 0) {
                    System.arraycopy(b, 0, b, off, n);
                }
                return n;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    /** OutputStream wrapper around UsbSerialPort.write(...) */
    static class UsbSerialOutputStream extends OutputStream {
        private final UsbSerialPort port;

        UsbSerialOutputStream(UsbSerialPort port) { this.port = port; }

        @Override public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override public void write(byte[] b, int off, int len) throws IOException {
            try {
                byte[] slice = (off == 0 && len == b.length) ? b : Arrays.copyOfRange(b, off, off + len);
                port.write(slice, 200); // ms timeout
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}