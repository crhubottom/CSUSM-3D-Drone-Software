//package com.example.airsimapp;
//
//
//        //needs to be ran separately, in IntelliJ or other Java IDE
//        //needs Maven imports below
//        /*
//         <dependency>
//            <groupId>io.dronefleet.mavlink</groupId>
//            <artifactId>mavlink</artifactId>
//            <version>1.1.11</version>
//        </dependency>
//        <dependency>
//            <groupId>com.fazecast</groupId>
//            <artifactId>jSerialComm</artifactId>
//            <version>2.10.4</version>
//        </dependency>
//*/
//
//
///*
//import com.fazecast.jSerialComm.SerialPort;
//import io.dronefleet.mavlink.MavlinkConnection;
//import io.dronefleet.mavlink.MavlinkMessage;
//import io.dronefleet.mavlink.common.*;
//import io.dronefleet.mavlink.minimal.Heartbeat;
//
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.nio.charset.StandardCharsets;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class JoystickBridge {
//
//    // Stick state
//    static final AtomicInteger pitchX = new AtomicInteger(0);     // -1000..1000
//    static final AtomicInteger rollY  = new AtomicInteger(0);     // -1000..1000
//    static final AtomicInteger yawR   = new AtomicInteger(0);     // -1000..1000
//    static final AtomicInteger thrZ   = new AtomicInteger(0);     // 0..1000
//
//    static final AtomicBoolean armed = new AtomicBoolean(false);
//
//    public static void main(String[] args) throws Exception {
//        String comPort = "COM6";
//        int baud = 115200;
//        int udpPort = 14560;
//
//        // --- Serial to Pixhawk ---
//        SerialPort port = SerialPort.getCommPort(comPort);
//        port.setBaudRate(baud);
//        port.setComPortTimeouts(
//                SerialPort.TIMEOUT_READ_BLOCKING,
//                0,
//                0
//        );
//        if (!port.openPort()) {
//            System.out.println("Failed to open " + comPort);
//            return;
//        }
//
//        MavlinkConnection mav = MavlinkConnection.create(port.getInputStream(), port.getOutputStream());
//
//        // --- Receive heartbeat to learn target sys/comp ---
//        int[] target = waitHeartbeat(mav, 10000);
//        int targetSys = target[0];
//        int targetComp = target[1];
//
//        if (targetSys < 0) {
//            System.out.println("No HEARTBEAT. Check COM port/baud.");
//            return;
//        }
//
//        System.out.println("Connected to autopilot sys=" + targetSys + " comp=" + targetComp);
//
//        // --- Listen for STATUSTEXT + ACK in background ---
//        new Thread(() -> listenMavlink(mav), "mav-recv").start();
//
//        // --- UDP listener for joystick ---
//        DatagramSocket sock = new DatagramSocket(udpPort);
//        System.out.println("Listening for UDP joystick on port " + udpPort);
//
//        new Thread(() -> udpReceiveLoop(sock, mav, targetSys, targetComp), "udp-recv").start();
//
//        // --- Send RC_CHANNELS_OVERRIDE at 20 Hz ---
//        int mySysId = 255, myCompId = 0;
//
//        while (true) {
//            // Gate throttle until armed (extra safety)
//            int z = armed.get() ? thrZ.get() : 0;
//
//            int rollPwm  = axisToPwm(rollY.get());  // CH1
//            int pitchPwm = axisToPwm(pitchX.get()); // CH2
//            int thrPwm   = throttleToPwm(z);        // CH3
//            int yawPwm   = axisToPwm(yawR.get());   // CH4
//
//            RcChannelsOverride rc = RcChannelsOverride.builder()
//                    .targetSystem(targetSys)
//                    .targetComponent(targetComp > 0 ? targetComp : 1)
//                    .chan1Raw(rollPwm)
//                    .chan2Raw(pitchPwm)
//                    .chan3Raw(thrPwm)
//                    .chan4Raw(yawPwm)
//                    // 0 = ignore channel (don’t override)
//                    .chan5Raw(0).chan6Raw(0).chan7Raw(0).chan8Raw(0)
//                    .build();
//
//            mav.send2(mySysId, myCompId, rc);
//            Thread.sleep(50);
//        }
//    }
//
//    // ---------- UDP parsing ----------
//
//    static void udpReceiveLoop(DatagramSocket sock, MavlinkConnection mav, int targetSys, int targetComp) {
//        byte[] buf = new byte[256];
//
//        while (true) {
//            try {
//                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
//                sock.receive(pkt);
//                String s = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8).trim();
//                System.out.println("UDP FROM " + pkt.getAddress() + ":" + pkt.getPort() + " -> " + s);
//
//                // "L,angle,strength"  left stick
//                // "R,angle,strength"  right stick
//                // "C,1,0" = ARM
//                // "C,0,0" = DISARM
//                String[] parts = s.split(",");
//                if (parts.length != 3) continue;
//
//                String type = parts[0];
//                double a = Double.parseDouble(parts[1]);
//                double st = Double.parseDouble(parts[2]);
//
//                if ("L".equals(type)) {
//                    if (st < 5) st = 0; // deadzone
//                    int[] xy = polarToXY(a, st);
//                    yawR.set(xy[0]);
//                    int throttle = stickYToThrottle(xy[1]);
//                    thrZ.set(armed.get() ? throttle : 0);
//
//                } else if ("R".equals(type)) {
//                    if (st < 5) st = 0;
//                    int[] xy = polarToXY(a, st);
//                    rollY.set(xy[0]);
//                    pitchX.set(xy[1]);
//
//                } else if ("C".equals(type)) {
//                    if ((int) a == 1) {
//                        thrZ.set(0); // safety
//                        sendArm(mav, targetSys, targetComp, true);
//                        System.out.println("ARM requested");
//                    } else {
//                        thrZ.set(0);
//                        armed.set(false);
//                        sendArm(mav, targetSys, targetComp, false);
//                        System.out.println("DISARM requested");
//                    }
//                }
//
//            } catch (Exception ignored) {}
//        }
//    }
//
//    // ---------- MAVLink helpers ----------
//
//    static int[] waitHeartbeat(MavlinkConnection mav, long timeoutMs) throws Exception {
//        long start = System.currentTimeMillis();
//        while (System.currentTimeMillis() - start < timeoutMs) {
//            MavlinkMessage<?> msg = mav.next();
//            Object p = msg.getPayload();
//            if (p instanceof Heartbeat) {
//                Heartbeat hb = (Heartbeat) p;
//                int sys = msg.getOriginSystemId();
//                int comp = msg.getOriginComponentId();
//                System.out.println("HEARTBEAT sys=" + sys + " comp=" + comp +
//                        " autopilot=" + hb.autopilot() + " type=" + hb.type());
//                return new int[]{sys, comp};
//            }
//        }
//        return new int[]{-1, -1};
//    }
//
//    static void listenMavlink(MavlinkConnection mav) {
//        while (true) {
//            try {
//                MavlinkMessage<?> msg = mav.next();
//                Object p = msg.getPayload();
//
//                if (p instanceof Statustext) {
//                    System.out.println("STATUSTEXT: " + ((Statustext)p).text());
//
//                } else if (p instanceof CommandAck) {
//                    CommandAck ack = (CommandAck) p;
//                    System.out.println("COMMAND_ACK: cmd=" + ack.command() + " result=" + ack.result());
//
//                    if (ack.command().entry() == MavCmd.MAV_CMD_COMPONENT_ARM_DISARM) {
//                        boolean ok = ack.result().entry() == MavResult.MAV_RESULT_ACCEPTED;
//                        armed.set(ok);
//                        if (!ok) thrZ.set(0);
//                        System.out.println(ok ? "ARMED (override active)" : "ARM FAILED");
//                    }
//                }
//            } catch (Exception e) {
//                System.out.println("MAVLink read error (continuing): " + e.getMessage());
//            }
//        }
//    }
//
//    static void sendArm(MavlinkConnection mav, int targetSys, int targetComp, boolean arm) throws Exception {
//        int mySysId = 255, myCompId = 0;
//
//        CommandLong cmd = CommandLong.builder()
//                .targetSystem(targetSys)
//                .targetComponent(targetComp > 0 ? targetComp : 1)
//                .command(MavCmd.MAV_CMD_COMPONENT_ARM_DISARM)
//                .confirmation(0)
//                .param1(arm ? 1 : 0)
//                .build();
//
//        mav.send2(mySysId, myCompId, cmd);
//    }
//
//    // ---------- PWM mapping for RC override ----------
//
//    // Map axis [-1000..1000] -> PWM [1000..2000] with center 1500
//    static int axisToPwm(int v) {
//        v = clamp(v, -1000, 1000);
//        return 1500 + (v * 500) / 1000;
//    }
//
//    // Map throttle [0..1000] -> PWM [1000..2000]
//    static int throttleToPwm(int z) {
//        z = clamp(z, 0, 1000);
//        return 1000 + z;
//    }
//
//    // ---------- Joystick conversion (your angle system) ----------
//
//    // 0 = right, -90 ~= up, +90 ~= down, +/-180 = left
//    static int[] polarToXY(double angleDeg, double strength) {
//        double mag = clamp((int) Math.round(strength), 0, 100) / 100.0;
//        double rad = Math.toRadians(-angleDeg); // negate clockwise
//
//        int x = (int) Math.round(Math.cos(rad) * mag * 1000.0); // right/left
//        int y = (int) Math.round(Math.sin(rad) * mag * 1000.0); // up/down
//
//        return new int[]{clamp(x, -1000, 1000), clamp(y, -1000, 1000)};
//    }
//
//    static int stickYToThrottle(int y) {
//        return clamp((y + 1000) / 2, 0, 1000);
//    }
//
//    static int clamp(int v, int lo, int hi) {
//        return Math.max(lo, Math.min(hi, v));
//    }
//}
//
// */