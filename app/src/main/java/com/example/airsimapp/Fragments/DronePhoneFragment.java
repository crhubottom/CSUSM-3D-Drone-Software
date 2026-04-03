package com.example.airsimapp.Fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.airsimapp.Activities.StartupActivity;
import com.example.airsimapp.AirSimFlightController;
import com.example.airsimapp.PixhawkMavlinkUsb;
import com.example.airsimapp.R;
import com.example.airsimapp.WebSocketClientTesting;
import com.example.airsimapp.flightControllerInterface;
import com.example.airsimapp.p2p.WifiP2pController;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.WebSocket;

public class DronePhoneFragment extends Fragment implements WifiP2pController.Listener {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    public static final String ip = "192.168.1.242";

    private static final int REQUEST_CODE_P2P_PERMISSIONS = 11;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private volatile boolean p2pConnected = false;
    private WebSocket websocketTest;
    public WebSocketClientTesting webSocket = new WebSocketClientTesting();

    private TextView output;
    private TextView GPSLocation;
    private TextView connectionStatus;

    private flightControllerInterface flightController;

    private Button btnOnOff;
    private Button btnDiscover;
    private Button btnSend;
    private Button connectDroneButton;

    private ListView listView;
    private PreviewView previewView;

    private WifiP2pController p2p;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private final List<String> peerNames = new ArrayList<>();
    private ArrayAdapter<String> peerAdapter;
    private PixhawkMavlinkUsb pixhawk;

    private String command;
    private String GPScoordinates = "No GPS data yet";
    private final Handler telemetryHandler = new Handler(Looper.getMainLooper());
    private WifiP2pController.VideoFeedSendReceive videoSendReceive;



    // Telemetry thread
    private final Runnable telemetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;

            if (p2p != null && p2p.isConnected()) {
                int[] motors = pixhawk.getMotorOutputsPercent();

                String telemetry =
                        "TEL," +
                                pixhawk.getAltitude() + "," +
                                pixhawk.getHeading() + "," +
                                pixhawk.getGroundSpeed() + "," +
                                motors[0] + "," +
                                motors[1] + "," +
                                motors[2] + "," +
                                motors[3] + "," +
                                (pixhawk.isArmed() ? 1 : 0) +
                                "\n";

                p2p.sendMessage(telemetry);
            }

            telemetryHandler.postDelayed(this, 250);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_drone_phone, container, false);

        Spinner flightControllerSpinner = rootView.findViewById(R.id.FlightControllerChoice);
        Button manualButton = rootView.findViewById(R.id.backButton);
        listView = rootView.findViewById(R.id.peerListView);
        btnDiscover = rootView.findViewById(R.id.discover);
         output = rootView.findViewById(R.id.readMsg);
         pixhawk=new PixhawkMavlinkUsb(requireContext());
        p2p = new WifiP2pController(requireContext(), this);




        peerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                peerNames
        );
        listView.setAdapter(peerAdapter);

        //discovery button
        btnDiscover.setOnClickListener(v -> {
            if (hasP2pPermissions()) {
                p2p.removeGroup();   // clear stale session
                p2p.discoverPeers();
            } else {
                requestP2pPermissions();
            }
        });

        //list of peers
        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            if (hasP2pPermissions()) {
                p2p.connectToPeer(i);
                //connect to peer when clicked
            } else {
                requestP2pPermissions();
            }
        });



            //list of flight controllers that can be used
        String[] controllers = {"Pixhawk", "Airsim"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                controllers
        );
        flightControllerSpinner.setAdapter(adapter);

        flightControllerSpinner.setSelection(0);
        flightControllerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedController = (String) parent.getItemAtPosition(position);
                if (selectedController.equals("AirSim")) {
                    flightController = new AirSimFlightController();
                    flightController.setMessageListener(message -> {
                        if (webSocket != null) {
                            webSocket.sendMessage(message);
                        }
                    });
                }else if (selectedController.equals("Pixhawk")) {
                    //unimplemented
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        //back button
        manualButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), StartupActivity.class);
            startActivity(intent);
        });

        if (allPermissionsGranted()) {
            //camera needs new implementation
            startCamera();
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        pixhawk.connect();
        if (p2p != null) {
            p2p.register();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        pixhawk.close();
        telemetryHandler.removeCallbacks(telemetryRunnable);

        if (p2p != null) {
            p2p.unregister();
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        telemetryHandler.removeCallbacks(telemetryRunnable);

        if (p2p != null) {
            p2p.shutdown();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasP2pPermissions() {
        boolean fineLocation = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearbyWifi = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED;

            return fineLocation && nearbyWifi;
        }

        return fineLocation;
    }

    private void requestP2pPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.NEARBY_WIFI_DEVICES
                    },
                    REQUEST_CODE_P2P_PERMISSIONS
            );
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_CODE_P2P_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                //startCamera();
            } else {
                Toast.makeText(requireContext(),
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_CODE_P2P_PERMISSIONS) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (!granted) {
                Toast.makeText(requireContext(),
                        "Wi-Fi Direct permissions denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    //adding new startCamera:
    private void startCamera()
    {
        ListenableFuture<ProcessCameraProvider> cPF = ProcessCameraProvider.getInstance(requireContext());
        cPF.addListener(()-> {
            try{
                ProcessCameraProvider cP = cPF.get();
                ImageAnalysis iA = new ImageAnalysis.Builder().setTargetResolution(new Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                iA.setAnalyzer(Executors.newSingleThreadExecutor(), image -> {
                    Bitmap bitmap = imageProxyToBitmap(image);
                    if(videoSendReceive != null && bitmap != null)//change
                    {
                        Log.d("CAMERA_DEBUG", "Bitmap is null? " + (bitmap == null));
                        sendFrame(bitmap);
                    }
                    image.close();
                });
                CameraSelector cS = CameraSelector.DEFAULT_BACK_CAMERA;
                cP.unbindAll();
                cP.bindToLifecycle(this, cS, iA);
            }catch(Exception e)
            {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    //use this for new camera stream
    //adding new sendFrame:
    private void sendFrame(Bitmap bp)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bp.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] frame = baos.toByteArray();
            videoSendReceive.dataOutputStream.writeInt(frame.length);//change to videoSendReceive
            videoSendReceive.dataOutputStream.write(frame);//change to videoSendReceive
            videoSendReceive.dataOutputStream.flush();//change to videoSendReceive
        }catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    //adding new ImageProxytoBitmap
    private Bitmap imageProxyToBitmap(ImageProxy i) {
        try {
            //YUV format for image
            ByteBuffer brightness = i.getPlanes()[0].getBuffer(); //brightness of image
            ByteBuffer blue = i.getPlanes()[1].getBuffer(); //blue color info
            ByteBuffer red = i.getPlanes()[2].getBuffer(); //red color info

            int ySize = brightness.remaining();
            int uSize = blue.remaining();
            int vSize = red.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            brightness.get(nv21, 0, ySize);
            red.get(nv21, ySize, vSize);
            blue.get(nv21, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage =
                    new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, i.getWidth(), i.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new android.graphics.Rect(0, 0, i.getWidth(), i.getHeight()),
                    50,
                    out
            );

            byte[] jpegBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // WifiP2pController.Listener callbacks
    //updates peer list
    @Override
    public void onPeersUpdated(List<WifiP2pDevice> peers) {
        if (!isAdded()) return;

        peerNames.clear();
        for (WifiP2pDevice device : peers) {
            peerNames.add(device.deviceName + "\n" + device.deviceAddress);
        }
        requireActivity().runOnUiThread(() -> peerAdapter.notifyDataSetChanged());
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (connectionStatus != null) {
                connectionStatus.setText(status);
            }
        });
    }


        //receives inputs from user phone
    @Override
    public void onMessageReceived(String message) {

        if (!isAdded()) return;
            //input packets are separated by \n, necessary as multiple commands can be sent simultaneously and may merge
        String[] packets = message.split("\n");

        for (String packet : packets) {

            if(packet.trim().isEmpty()) continue;

            String[] parts = packet.split(","); //individual parts of each packet are separated by commas

            if(parts.length < 6) continue;  //if more, something went wrong

            if(parts[0].equals("CTRL")){        //CNTRL packets used for manual control

                int roll = Integer.parseInt(parts[1]);
                int pitch = Integer.parseInt(parts[2]);
                int yaw = Integer.parseInt(parts[3]);
                int throttle = Integer.parseInt(parts[4]);
                int armed = Integer.parseInt(parts[5]);
                pixhawk.setRoll(roll);
                pixhawk.setPitch(pitch);
                pixhawk.setYaw(yaw);
                pixhawk.setThrottle(throttle);
                if (armed == 1 && !pixhawk.isArmed()) {
                    pixhawk.arm();
                }

                if (armed == 0 && pixhawk.isArmed()) {
                    pixhawk.setThrottle(0);
                    pixhawk.disarm();
                }
                //displays control packets on drone phone screen for debug purposes
                String display =
                        "CONTROL PACKET\n\n" +
                                "Roll: " + roll + "\n" +
                                "Pitch: " + pitch + "\n" +
                                "Yaw: " + yaw + "\n" +
                                "Throttle: " + throttle + "\n" +
                                "Armed: " + (armed == 1 ? "YES" : "NO");

                requireActivity().runOnUiThread(() -> {
                    output.setText(display);
                });
            }
        }
    }

    @Override
    public void onVideoFrameReceived(byte[] data) {

    }

    @Override
    public void onSocketConnected(boolean isHost) {
        if (!isAdded()) return;

        p2pConnected = true;

        telemetryHandler.removeCallbacks(telemetryRunnable);
        telemetryHandler.post(telemetryRunnable);

        requireActivity().runOnUiThread(() -> {
            Toast.makeText(
                    requireContext(),
                    isHost ? "Socket Connected (Host)" : "Socket Connected (Client)",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    @Override
    public void onError(String error) {
        p2pConnected = false;

        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (connectionStatus != null) {
                connectionStatus.setText(error);
            }
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
        });
    }
}