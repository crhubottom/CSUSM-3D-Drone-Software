package com.example.airsimapp.p2p;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
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
        void onVideoFrameReceived(byte[] data);
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
    private TelemetrySendReceive telemetrySendReceive;
    private VideoFeedSendReceive videoFeedSendReceive;

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
        return telemetrySendReceive != null && telemetrySendReceive.isAliveAndConnected();
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
        if (!isConnected()) {
            listener.onError("No socket connection available");
            return;
        }
        telemetrySendReceive.write(message.getBytes());
    }
    public void sendFrame(Bitmap bp)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bp.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] frame = baos.toByteArray();
            videoFeedSendReceive.dataOutputStream.writeInt(frame.length);//change to videoSendReceive
            videoFeedSendReceive.dataOutputStream.write(frame);//change to videoSendReceive
            videoFeedSendReceive.dataOutputStream.flush();//change to videoSendReceive
        }catch(Exception e)
        {
            e.printStackTrace();
        }
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
        if (telemetrySendReceive != null && videoFeedSendReceive != null) {
            telemetrySendReceive.close();
            videoFeedSendReceive.close();
            telemetrySendReceive = null;
            videoFeedSendReceive = null;
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
private class TelemetrySendReceive extends Thread {
        private final Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private volatile boolean closed = false;

        public TelemetrySendReceive(Socket socket) {
            this.socket = socket;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                listener.onError("Telemetry stream error: " + e.getMessage());
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

            if (telemetrySendReceive == this) {
                telemetrySendReceive = null;
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
    public class VideoFeedSendReceive extends Thread { //add video send receive class to send video frames
        private final Socket socket;
        public DataOutputStream dataOutputStream;
        public DataInputStream dataInputStream;
        private volatile boolean closed = false;

        public VideoFeedSendReceive(Socket socket) throws IOException {
            this.socket = socket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
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
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void run() {
            while (isAliveAndConnected()) {
                try {
                    while(isAliveAndConnected())
                    {
                        int length = dataInputStream.readInt();
                        if(length <=0 || length > 10_000_000)
                        {
                            throw new IOException("Invalid frame size: " + length);
                        }
                        byte[] data = new byte[length];
                        dataInputStream.readFully(data);

                        mainHandler.post(()->listener.onVideoFrameReceived(data));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                close();
            }
        }
    }

    public class ServerClass extends Thread {
        private Socket telemSocket; //rename firsts socket to telemSocket
        private Socket videoSocket; //added new videoSocket
        private ServerSocket telemServerSocket; //rename first server socket to telemServerSocket
        private ServerSocket videoServerSocket; //added new videoServerSocket

        private TelemetrySendReceive telemSendReceive; //added specific sendReceive for telemetry
        private VideoFeedSendReceive videoSendReceive; //added specific sendReceive for video feed
        private volatile boolean
                closed = false;

        @Override
        public void run() {
            try {
                telemServerSocket = new ServerSocket(6000);
                videoServerSocket = new ServerSocket(7000); //added new server socket at port 7000 for video

                new Thread(() -> { //run telemetry on one thread
                    try {
                        telemSocket = telemServerSocket.accept();
                        if (closed) return;
                        telemSendReceive = new TelemetrySendReceive(telemSocket);
                        telemSendReceive.start();
                        checkIfBothConnected();
                    } catch (IOException e) {
                        handleError("Error telemetry: " + e.getMessage());
                    }
                }).start();
                new Thread(() -> { //run video on another thread
                    try {
                        videoSocket = videoServerSocket.accept();
                        if (closed) return;
                        videoSendReceive = new VideoFeedSendReceive(videoSocket);
                        videoFeedSendReceive = videoSendReceive;
                        videoSendReceive.start();
                        checkIfBothConnected();
                    } catch (IOException e) {
                        handleError("Error Video: " + e.getMessage());
                    }
                }).start();
            } catch (IOException e) {
                handleError("Error server startup: " + e.getMessage());
            }
        }
        private synchronized void checkIfBothConnected()
        {
            if(telemSocket != null && videoSocket != null)
            {
                mainHandler.post(()->listener.onSocketConnected(true));
            }
        }

        private void handleError(String m)
        {
            if(!closed)
            {
                mainHandler.post(()-> listener.onError(m));
            }
        }
        public void close() {
            closed = true;
            try {
                if (telemSocket != null && !telemSocket.isClosed())
                {
                    telemSocket.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (videoSocket != null && !videoSocket.isClosed())
                {
                    videoSocket.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (telemServerSocket != null && !telemServerSocket.isClosed()) telemServerSocket.close();
            } catch (Exception ignored) {
            }
            try {
                if (videoServerSocket != null && !videoServerSocket.isClosed()) videoServerSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public class ClientClass extends Thread {
        private final Socket telemSocket;
        private final Socket videoSocket;
        private TelemetrySendReceive telemSendReceive;
        private VideoFeedSendReceive videoSendReceive;
        private final String hostAddress;
        private volatile boolean closed = false;

        public ClientClass(InetAddress hostAddress) {
            this.hostAddress = hostAddress.getHostAddress();
            this.telemSocket = new Socket();
            this.videoSocket = new Socket();
        }

        @Override
        public void run() {
            int attempts = 0;

            while (attempts < 10 && !closed) {
                try {
                    Thread.sleep(1000);
                    telemSocket.connect(new InetSocketAddress(hostAddress, 6000), 3000);
                    videoSocket.connect(new InetSocketAddress(hostAddress, 7000), 3000);

                    if (closed) return;

                    telemSendReceive = new TelemetrySendReceive(telemSocket);
                    telemSendReceive.start();

                    videoSendReceive = new VideoFeedSendReceive(videoSocket);
                    videoFeedSendReceive = videoSendReceive;
                    videoSendReceive.start();

                    mainHandler.post(()->listener.onSocketConnected(false));
                    return;

                } catch (Exception e) {
                    attempts++;
                    try{
                        if(telemSocket != null)
                        {
                            telemSocket.close();
                        }
                    }catch(Exception ignored)
                    {
                    }
                    try{
                        if(videoSocket != null)
                        {
                            videoSocket.close();
                        }
                    }catch(Exception ignored)
                    {
                    }
                }
            }
            if (!closed) {
                mainHandler.post(() -> listener.onError("Client could not connect after retries"));
            }
        }

        public void close() {
            closed = true;
            try {
                if (!telemSocket.isClosed()) telemSocket.close();
            } catch (Exception ignored) {
            }
            try
            {
                if(!videoSocket.isClosed()) videoSocket.close();
            }catch (Exception ignored)
            {
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