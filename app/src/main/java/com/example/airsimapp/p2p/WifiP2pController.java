package com.example.airsimapp.p2p;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WifiP2pController {

    public interface Listener {
        void onPeersUpdated(List<WifiP2pDevice> peers);
        void onConnectionStatusChanged(String status);
        void onMessageReceived(String message);
        void onCameraBytesReceieved(byte[] data);
        void onSocketConnected(boolean isHost);
        void onError(String error);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    private final List<WifiP2pDevice> peers = new ArrayList<>();
    private WifiP2pDevice[] deviceArray = new WifiP2pDevice[0];

    private ServerClass serverClass;
    private ClientClass clientClass;
    private static final int PORT_CONTROL = 6000;
    private static final int PORT_SECOND = 6001;
    private static final int CHANNEL_CONTROL = 1;
    private static final int CHANNEL_SECOND = 2;
    private ServerClass serverControl;
    private ServerClass serverSecond;

    private ClientClass clientControl;
    private ClientClass clientSecond;

    private SendReceive sendReceiveControl;
    private SendReceive sendReceiveSecond;

    private boolean receiverRegistered = false;

    public WifiP2pController(@NonNull Context context, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;

        manager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager != null) {
            channel = manager.initialize(this.context, Looper.getMainLooper(), null);
        }

        setupIntentFilter();
        setupReceiver();
    }

    private void setupIntentFilter() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void setupReceiver() {
        receiver = new WifiBR(this);
    }

    public void register() {
        if (receiverRegistered) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, intentFilter);
        }
        receiverRegistered = true;
    }

    public void unregister() {
        if (!receiverRegistered) return;
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        receiverRegistered = false;
    }

    public List<WifiP2pDevice> getPeers() {
        return new ArrayList<>(peers);
    }

    public WifiP2pDevice getPeer(int index) {
        if (index < 0 || index >= deviceArray.length) return null;
        return deviceArray[index];
    }

    public boolean isConnected() {
        return sendReceiveControl != null && sendReceiveControl.isAliveAndConnected();
    }
    public boolean isCameraConnected() {
        return sendReceiveSecond != null && sendReceiveSecond.isAliveAndConnected();
    }
    @SuppressLint("MissingPermission")
    public void discoverPeers() {
        if (manager == null || channel == null) {
            listener.onError("Wi-Fi P2P not available");
            return;
        }

        if (!hasRequiredPermissions()) {
            listener.onError("Missing Wi-Fi Direct permissions");
            return;
        }

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listener.onConnectionStatusChanged("Discovery started");
            }

            @Override
            public void onFailure(int reason) {
                //listener.onError("discoverPeers failed: " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void createGroup() {
        if (manager == null || channel == null) {
            listener.onError("Wi-Fi P2P not available");
            return;
        }

        if (!hasRequiredPermissions()) {
            listener.onError("Missing Wi-Fi Direct permissions");
            return;
        }

        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listener.onConnectionStatusChanged("Group created");
                requestConnectionInfo();
            }

            @Override
            public void onFailure(int reason) {
               // listener.onError("createGroup failed: " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void removeGroup() {
        if (manager == null || channel == null) return;

        if (!hasRequiredPermissions()) {
            listener.onError("Missing Wi-Fi Direct permissions");
            return;
        }

        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //listener.onConnectionStatusChanged("Group removed");
            }

            @Override
            public void onFailure(int reason) {
                //listener.onError("removeGroup failed: " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void stopPeerDiscovery() {
        if (manager == null || channel == null) return;

        if (!hasRequiredPermissions()) {
            listener.onError("Missing Wi-Fi Direct permissions");
            return;
        }

        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listener.onConnectionStatusChanged("Peer discovery stopped");
            }

            @Override
            public void onFailure(int reason) {
                listener.onError("stopPeerDiscovery failed: " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void connectToPeer(WifiP2pDevice device) {
        if (device == null) {
            listener.onError("Device is null");
            return;
        }

        if (manager == null || channel == null) {
            listener.onError("Wi-Fi P2P not available");
            return;
        }

        if (!hasRequiredPermissions()) {
            listener.onError("Missing Wi-Fi Direct permissions");
            return;
        }

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listener.onConnectionStatusChanged("Connected to " + device.deviceName);
                requestConnectionInfo();
            }

            @Override
            public void onFailure(int reason) {
                listener.onError("connect failed: " + reason);
            }
        });
    }

    public void connectToPeer(int index) {
        WifiP2pDevice device = getPeer(index);
        if (device == null) {
            listener.onError("Invalid peer index");
            return;
        }
        connectToPeer(device);
    }

    public void sendMessage(String message) {
        if (sendReceiveControl == null || !sendReceiveControl.isAliveAndConnected()) {
            listener.onError("No control socket connection available");
            return;
        }
        sendReceiveControl.write(message.getBytes());
    }

    public void sendCameraBytes(byte[] jpegBytes) {
        if (sendReceiveSecond == null || !sendReceiveSecond.isAliveAndConnected()) {
            listener.onError("No second socket connection available");
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(4 + jpegBytes.length);
        buffer.putInt(jpegBytes.length);
        buffer.put(jpegBytes);

        sendReceiveSecond.write(buffer.array());
    }


    public void shutdown() {
        closeControlSocketConnection();
        closeSecondSocketConnection();
        if (clientClass != null) {
            clientClass.close();
            clientClass = null;
        }

        if (serverClass != null) {
            serverClass.close();
            serverClass = null;
        }

        stopPeerDiscovery();
        removeGroup();
    }

    private void closeControlSocketConnection() {
        if (sendReceiveControl != null) {
            sendReceiveControl.close();
            sendReceiveControl = null;
        }
    }

    private void closeSecondSocketConnection() {
        if (sendReceiveSecond != null) {
            sendReceiveSecond.close();
            sendReceiveSecond = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void requestPeers() {
        if (manager == null || channel == null) return;

        if (!hasRequiredPermissions()) {
            listener.onError("Missing permissions for peer discovery");
            return;
        }

        manager.requestPeers(channel, peerListListener);
    }

    @SuppressLint("MissingPermission")
    private void requestConnectionInfo() {
        if (manager == null || channel == null) return;

        if (!hasRequiredPermissions()) {
            listener.onError("Missing permissions for connection info");
            return;
        }

        manager.requestConnectionInfo(channel, connectionInfoListener);
    }

    private boolean hasRequiredPermissions() {
        boolean fineLocation =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearbyWifi =
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                            == PackageManager.PERMISSION_GRANTED;
            return fineLocation && nearbyWifi;
        }

        return fineLocation;
    }

    private final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            peers.clear();
            peers.addAll(peerList.getDeviceList());

            deviceArray = peerList.getDeviceList().toArray(new WifiP2pDevice[0]);
            listener.onPeersUpdated(new ArrayList<>(peers));

            if (peers.isEmpty()) {
                listener.onConnectionStatusChanged("No devices found");
            }
        }
    };
    private final WifiP2pManager.ConnectionInfoListener connectionInfoListener =
            new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    if (!info.groupFormed) {
                        listener.onConnectionStatusChanged("Group not formed");
                        return;
                    }

                    if (info.isGroupOwner) {
                        listener.onConnectionStatusChanged("Starting server");

                        if (serverControl == null || !serverControl.isAlive()) {
                            serverControl = new ServerClass(PORT_CONTROL, CHANNEL_CONTROL);
                            serverControl.start();
                        }

                        if (serverSecond == null || !serverSecond.isAlive()) {
                            serverSecond = new ServerClass(PORT_SECOND, CHANNEL_SECOND);
                            serverSecond.start();
                        }

                    } else {
                        listener.onConnectionStatusChanged("Starting client");

                        mainHandler.postDelayed(() -> {
                            if (clientControl == null || !clientControl.isAlive()) {
                                clientControl = new ClientClass(
                                        info.groupOwnerAddress,
                                        PORT_CONTROL,
                                        CHANNEL_CONTROL
                                );
                                clientControl.start();
                            }

                            if (clientSecond == null || !clientSecond.isAlive()) {
                                clientSecond = new ClientClass(
                                        info.groupOwnerAddress,
                                        PORT_SECOND,
                                        CHANNEL_SECOND
                                );
                                clientSecond.start();
                            }
                        }, 2000);
                    }
                }
            };

    private class SendReceive extends Thread {
        private final Socket socket;
        private final int channelType;
        private InputStream inputStream;
        private OutputStream outputStream;
        private volatile boolean closed = false;

        public SendReceive(Socket socket, int channelType) {
            this.socket = socket;
            this.channelType = channelType;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                listener.onError("Socket stream error: " + e.getMessage());
            }
        }

        public boolean isAliveAndConnected() {
            return !closed
                    && socket != null
                    && socket.isConnected()
                    && !socket.isClosed();
        }

        public synchronized void close() {
            closed = true;

            try {
                if (inputStream != null) inputStream.close();
            } catch (Exception ignored) {
            }

            try {
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {
            }

            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {
            }

            if (channelType == CHANNEL_CONTROL && sendReceiveControl == this) {
                sendReceiveControl = null;
            } else if (channelType == CHANNEL_SECOND && sendReceiveSecond == this) {
                sendReceiveSecond = null;
            }
        }

        @Override
        public void run() {
            if (channelType == CHANNEL_CONTROL) {
                runTextLoop();
            } else if (channelType == CHANNEL_SECOND) {
                runFrameLoop();
            }
        }
        private void runTextLoop() {
            byte[] buffer = new byte[1024];
            int bytes;

            try {
                while (isAliveAndConnected()) {
                    bytes = inputStream.read(buffer);

                    if (bytes == -1) {
                        break;
                    }

                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);

                        mainHandler.post(() -> listener.onMessageReceived(message));
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    mainHandler.post(() -> listener.onError("Control connection closed"));
                }
            } finally {
                close();
            }
        }
        private void runFrameLoop() {
            try {
                DataInputStream dis = new DataInputStream(inputStream);

                while (isAliveAndConnected()) {
                    int frameLength = dis.readInt();
                    if (frameLength <= 0 || frameLength > 5_000_000) {
                        throw new IOException("Invalid frame size: " + frameLength);
                    }

                    byte[] frame = new byte[frameLength];
                    dis.readFully(frame);

                    mainHandler.post(() -> listener.onCameraBytesReceieved(frame));
                }
            } catch (IOException e) {
                if (!closed) {
                    mainHandler.post(() -> listener.onError("Second socket frame read failed: " + e.getMessage()));
                }
            } finally {
                close();
            }
        }
        public void write(byte[] bytes) {
            new Thread(() -> {
                try {
                    if (isAliveAndConnected() && outputStream != null) {
                        outputStream.write(bytes);
                        outputStream.flush();
                    } else {
                        close();
                        mainHandler.post(() -> listener.onError(
                                channelType == CHANNEL_CONTROL
                                        ? "Control socket is not connected"
                                        : "Second socket is not connected"
                        ));
                    }
                } catch (IOException e) {
                    close();
                    mainHandler.post(() -> listener.onError("Send error: " + e.getMessage()));
                }
            }).start();
        }
    }

    public class ServerClass extends Thread {
        private final int port;
        private final int channelType;
        private Socket socket;
        private ServerSocket serverSocket;
        private volatile boolean closed = false;

        public ServerClass(int port, int channelType) {
            this.port = port;
            this.channelType = channelType;
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();

                if (closed) return;

                if (channelType == CHANNEL_CONTROL) {
                    closeControlSocketConnection();
                    sendReceiveControl = new SendReceive(socket, CHANNEL_CONTROL);
                    sendReceiveControl.start();
                } else {
                    closeSecondSocketConnection();
                    sendReceiveSecond = new SendReceive(socket, CHANNEL_SECOND);
                    sendReceiveSecond.start();
                }

                mainHandler.post(() -> listener.onSocketConnected(true));

            } catch (IOException e) {
                if (!closed) {
                    mainHandler.post(() -> listener.onError(
                            "Server error on port " + port + ": " + e.getMessage()
                    ));
                }
            }
        }

        public void close() {
            closed = true;

            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {
            }

            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public class ClientClass extends Thread {
        private final Socket socket;
        private final String hostAddress;
        private final int port;
        private final int channelType;
        private volatile boolean closed = false;

        public ClientClass(InetAddress hostAddress, int port, int channelType) {
            this.hostAddress = hostAddress.getHostAddress();
            this.port = port;
            this.channelType = channelType;
            this.socket = new Socket();
        }

        @Override
        public void run() {
            int attempts = 0;

            while (attempts < 10 && !closed) {
                Socket tempSocket = new Socket();
                try {
                    Thread.sleep(1000);
                    tempSocket.connect(new InetSocketAddress(hostAddress, port), 3000);

                    if (closed) {
                        tempSocket.close();
                        return;
                    }

                    if (channelType == CHANNEL_CONTROL) {
                        closeControlSocketConnection();
                        sendReceiveControl = new SendReceive(tempSocket, CHANNEL_CONTROL);
                        sendReceiveControl.start();
                    } else {
                        closeSecondSocketConnection();
                        sendReceiveSecond = new SendReceive(tempSocket, CHANNEL_SECOND);
                        sendReceiveSecond.start();
                    }

                    mainHandler.post(() -> listener.onSocketConnected(false));
                    return;

                } catch (Exception e) {
                    attempts++;
                    try {
                        tempSocket.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            if (!closed) {
                mainHandler.post(() -> listener.onError(
                        "Client could not connect on port " + port + " after retries"
                ));
            }
        }

        public void close() {
            closed = true;
            try {
                if (!socket.isClosed()) socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void notifyStatus(String status) {
        listener.onConnectionStatusChanged(status);
    }

    public void handlePeersChanged() {
        requestPeers();
    }

    public void handleConnectionChanged() {
        requestConnectionInfo();
    }
}