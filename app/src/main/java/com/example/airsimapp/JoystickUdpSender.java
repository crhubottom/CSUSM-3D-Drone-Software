package com.example.airsimapp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class JoystickUdpSender {
    //this class is only used if trying to control drone via android emulator
    //middleman between emulator and drone, emulator cannot access USB devices directly
    //needs seperate server running on computer to send data to drone

    private final InetAddress host;
    private final int port;
    private final DatagramSocket socket;

    // Single background thread for all UDP sends (prevents UI-thread network)
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Optional: keep only the most recent joystick message to avoid backlog
    private final AtomicReference<String> latest = new AtomicReference<>(null);

    public JoystickUdpSender(String hostIp, int port) throws Exception {
        this.host = InetAddress.getByName(hostIp);
        this.port = port;
        this.socket = new DatagramSocket();

        // Worker loop: sends most recent message (drops older ones)
        io.execute(() -> {
            while (!socket.isClosed()) {
                try {
                    String msg = latest.getAndSet(null);
                    if (msg == null) {
                        Thread.sleep(5);
                        continue;
                    }
                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(data, data.length, host, port);
                    socket.send(packet);
                } catch (Exception ignored) {}
            }
        });
    }

    /** Non-blocking: queues message to be sent on background thread */
    public void send(String msg) {
        latest.set(msg);
    }

    /** Non-blocking joystick helper */
    public void sendStick(char stick, double angle, double strength) {
        send(stick + "," + angle + "," + strength);
    }

    public void close() {
        try { socket.close(); } catch (Exception ignored) {}
        io.shutdownNow();
    }
}