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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WifiP2pController {

    public interface Listener {
        void onPeersUpdated(List<WifiP2pDevice> peers);
        void onConnectionStatusChanged(String status);
        void onMessageReceived(String message);
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
    private SendReceive sendReceive;

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
        return sendReceive != null && sendReceive.isAliveAndConnected();
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
                listener.onError("discoverPeers failed: " + reason);
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
                listener.onError("createGroup failed: " + reason);
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
                listener.onConnectionStatusChanged("Group removed");
            }

            @Override
            public void onFailure(int reason) {
                listener.onError("removeGroup failed: " + reason);
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
        if (!isConnected()) {
            listener.onError("No socket connection available");
            return;
        }
        sendReceive.write(message.getBytes());
    }

    public void shutdown() {
        closeSocketConnection();

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

    private void closeSocketConnection() {
        if (sendReceive != null) {
            sendReceive.close();
            sendReceive = null;
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
                        if (serverClass == null || !serverClass.isAlive()) {
                            serverClass = new ServerClass();
                            serverClass.start();
                        }
                    } else {
                        listener.onConnectionStatusChanged("Starting client");
                        if (clientClass == null || !clientClass.isAlive()) {
                            mainHandler.postDelayed(() -> {
                                clientClass = new ClientClass(info.groupOwnerAddress);
                                clientClass.start();
                            }, 2000);
                        }
                    }
                }
            };

    private class SendReceive extends Thread {
        private final Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private volatile boolean closed = false;

        public SendReceive(Socket socket) {
            this.socket = socket;
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

            if (sendReceive == this) {
                sendReceive = null;
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isAliveAndConnected()) {
                try {
                    bytes = inputStream.read(buffer);

                    if (bytes == -1) {
                        break;
                    }

                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        mainHandler.post(() -> listener.onMessageReceived(message));
                    }
                } catch (IOException e) {
                    if (!closed) {
                        mainHandler.post(() -> listener.onError("Connection closed"));
                    }
                    break;
                }
            }

            close();
        }

        public void write(byte[] bytes) {
            new Thread(() -> {
                try {
                    if (isAliveAndConnected() && outputStream != null) {
                        outputStream.write(bytes);
                        outputStream.flush();
                    } else {
                        close();
                        mainHandler.post(() -> listener.onError("Socket is not connected"));
                    }
                } catch (IOException e) {
                    close();
                    mainHandler.post(() -> listener.onError("Send error: " + e.getMessage()));
                }
            }).start();
        }
    }

    public class ServerClass extends Thread {
        private Socket socket;
        private ServerSocket serverSocket;
        private volatile boolean closed = false;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(6000);
                socket = serverSocket.accept();

                if (closed) return;

                closeSocketConnection();
                sendReceive = new SendReceive(socket);
                sendReceive.start();

                mainHandler.post(() -> listener.onSocketConnected(true));
            } catch (IOException e) {
                if (!closed) {
                    mainHandler.post(() -> listener.onError("Server error: " + e.getMessage()));
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
        private volatile boolean closed = false;

        public ClientClass(InetAddress hostAddress) {
            this.hostAddress = hostAddress.getHostAddress();
            this.socket = new Socket();
        }

        @Override
        public void run() {
            int attempts = 0;

            while (attempts < 10 && !closed) {
                try {
                    Thread.sleep(1000);
                    socket.connect(new InetSocketAddress(hostAddress, 6000), 3000);

                    if (closed) return;

                    closeSocketConnection();
                    sendReceive = new SendReceive(socket);
                    sendReceive.start();

                    mainHandler.post(() -> listener.onSocketConnected(false));
                    return;

                } catch (Exception e) {
                    attempts++;
                }
            }

            if (!closed) {
                mainHandler.post(() -> listener.onError("Client could not connect after retries"));
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