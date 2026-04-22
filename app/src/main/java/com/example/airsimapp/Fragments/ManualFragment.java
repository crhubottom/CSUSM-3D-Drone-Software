package com.example.airsimapp.Fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.airsimapp.Activities.StartupActivity;
import com.example.airsimapp.Activities.UserActivity;
import com.example.airsimapp.JoystickView;
import com.example.airsimapp.R;
import com.example.airsimapp.p2p.WifiP2pController;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

public class ManualFragment extends Fragment implements WifiP2pController.Listener {

    private static final String TAG = "ManualFragment";
    private static final int REQUEST_CODE_P2P = 2001;

    private WifiP2pController p2p;
    private int armed = 0;

    private ImageView remoteView;
    private Button startServer;
    private TextView textAltitude;
    private TextView textHeading;
    private TextView textSpeed;
    private TextView textMotor1;
    private TextView textMotor2;
    private TextView textMotor3;
    private TextView textMotor4;

    private int roll = 0;
    private int pitch = 0;
    private int yaw = 0;
    private int throttle = 0;
    private volatile boolean p2pConnected = false;
    private volatile boolean serverRequested = false;

    private final Handler handler = new Handler(Looper.getMainLooper());



    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_manual, container, false);

        startServer = rootView.findViewById(R.id.startServer);
        Button armButton = rootView.findViewById(R.id.TakeoffLanding);
        Button backButton = rootView.findViewById(R.id.backButton2);
        ImageButton autoPilotButton = rootView.findViewById(R.id.menuButton);

        textAltitude = rootView.findViewById(R.id.textAltitude);
        textHeading = rootView.findViewById(R.id.textHeading);
        textSpeed = rootView.findViewById(R.id.textSpeed);
        textMotor1 = rootView.findViewById(R.id.textMotor1);
        textMotor2 = rootView.findViewById(R.id.textMotor2);
        textMotor3 = rootView.findViewById(R.id.textMotor3);
        textMotor4 = rootView.findViewById(R.id.textMotor4);
        remoteView = rootView.findViewById(R.id.remoteCameraView);


        p2p = new WifiP2pController(requireContext(), this);

        startServer.setText("Start Server");
        startServer.setEnabled(true);
        startServer.setOnClickListener(v -> startServerFlow());

        armButton.setBackgroundColor(Color.parseColor("#32CD32"));
        armButton.setText("ARM");

        armButton.setOnClickListener(v -> {
                //should be changed to read actual ARM status from drone phone
            armed = (armed == 0) ? 1 : 0;

            if (armed == 1) {
                armButton.setBackgroundColor(Color.parseColor("#B22222"));
                armButton.setText("DISARM");
            } else {
                armButton.setBackgroundColor(Color.parseColor("#32CD32"));
                armButton.setText("ARM");
            }

            sendControlPacket();        //sends control packet to arm/disarm drone
        });
                //back button
        backButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), StartupActivity.class))
        );
            //autopilot overlay
        autoPilotButton.setOnClickListener(v -> {
            if (getActivity() instanceof UserActivity) {
                ((UserActivity) getActivity()).switchFragment(UserActivity.getAutopilotFragment());
            }
        });

        JoystickView joystickLeft = rootView.findViewById(R.id.joystick2);
        joystickLeft.noLockLeftY();
        joystickLeft.setY(450);
        JoystickView joystickRight = rootView.findViewById(R.id.joystick);
                //left joystick
        joystickLeft.setJoystickListener((angle, strength) -> {

            int[] xy = polarToXY(angle, strength, 5);

            yaw = xy[0];
            throttle = stickYToThrottle(xy[1]);

            sendControlPacket(); //sends packet whenever joystick is moved
        });
                //right joystick
        joystickRight.setJoystickListener((angle, strength) -> {

            int[] xy = polarToXY(angle, strength, 5);

            roll = xy[0];
            pitch = -xy[1];

            sendControlPacket();        //sends packet whenever joystick is moved
        });


        return rootView;
    }
    private void sendControlPacket() {

        if (p2p != null && p2p.isConnected()) {
                //sends manual control packet
            String msg = "CTRL," +
                    roll + "," +
                    pitch + "," +
                    yaw + "," +
                    throttle + "," +
                    armed +
                    "\n";

            p2p.sendMessage(msg);

        }
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (p2p != null) {
            p2p.register();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (p2p != null) {
            p2p.unregister();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);

        if (p2p != null) {
            p2p.shutdown();
        }
    }
            //start p2p server
    private void startServerFlow() {
        if (!hasP2pPermissions()) {
            requestP2pPermissions();
            return;
        }

        serverRequested = true;
        p2pConnected = false;
        startServer.setEnabled(false);
        startServer.setText("Starting...");
        p2p.createGroup();

    }
                        //permission checks
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
                //permission requests
    private void requestP2pPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.NEARBY_WIFI_DEVICES
                    },
                    REQUEST_CODE_P2P
            );
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_P2P
            );
        }
    }
                    //if user declines permissions, server will not start
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_P2P) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                startServerFlow();
            } else {
                Toast.makeText(requireContext(), "Wi-Fi Direct permissions denied", Toast.LENGTH_SHORT).show();
                startServer.setEnabled(true);
                startServer.setText("Start Server");
            }
        }
    }


    //left joystick up/down is throttle only
    static int stickYToThrottle(int y) {
        int t = (y + 1000) / 2;
        return clamp(t, 0, 1000);
    }
    //helper method for polarToXY
    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }


            //changes on screen joystick outputs (-180-180) to Mavlink inputs (-1000-1000)
    static int[] polarToXY(double angleDeg, double strength, int deadzone) {
        if (strength < deadzone) strength = 0;

        double mag = clamp((int) Math.round(strength), 0, 100) / 100.0;
        double rad = Math.toRadians(-angleDeg);

        int x = (int) Math.round(Math.cos(rad) * mag * 1000.0);
        int y = (int) Math.round(Math.sin(rad) * mag * 1000.0);

        return new int[]{clamp(x, -1000, 1000), clamp(y, -1000, 1000)};
    }

        //needs new implementation


    @Override
    public void onPeersUpdated(List<WifiP2pDevice> peers) {
        // Host fragment does not need peer list UI
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if ("Group created".equals(status) || "Starting server".equals(status)) {
                startServer.setText("Server Ready");
                startServer.setEnabled(false);
            } else if ("Group removed".equals(status)) {
                p2pConnected = false;
                serverRequested = false;
                startServer.setText("Start Server");
                startServer.setEnabled(true);
            } else if ("Group not formed".equals(status) && serverRequested) {
                startServer.setText("Start Server");
                startServer.setEnabled(true);
            }

            if (!"Peer discovery stopped".equals(status)) {
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show();
            }
        });
    }


        //receives telemetry from drone phone, updates on screen
    @Override
    public void onMessageReceived(String message) {
        if (!isAdded()) return;

        String[] packets = message.split("\n");

        for (String packet : packets) {
            if (packet.trim().isEmpty()) continue;

            String[] parts = packet.split(",");
            if (parts.length < 9) continue;

            if (parts[0].equals("TEL")) {
                double altitude = Double.parseDouble(parts[1]);
                double heading = Double.parseDouble(parts[2]);
                double speed = Double.parseDouble(parts[3]);

                int m1 = Integer.parseInt(parts[4]);
                int m2 = Integer.parseInt(parts[5]);
                int m3 = Integer.parseInt(parts[6]);
                int m4 = Integer.parseInt(parts[7]);

                int armed = Integer.parseInt(parts[8]);

                requireActivity().runOnUiThread(() -> {
                    textAltitude.setText(String.format(Locale.US, "Alt: %.1f m", altitude));
                    textHeading.setText(String.format(Locale.US, "Head: %.0f°", heading));
                    textSpeed.setText(String.format(Locale.US, "Speed: %.1f m/s", speed));

                    textMotor1.setText("M1: " + m1 + "%");
                    textMotor2.setText("M2: " + m2 + "%");
                    textMotor3.setText("M3: " + m3 + "%");
                    textMotor4.setText("M4: " + m4 + "%");

                    startServer.setText(armed == 1 ? "ARMED" : "DISARMED");
                });
            }
        }
    }

    @Override
    public void onCameraBytesReceieved(byte[] data) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null) {
                remoteView.setImageBitmap(bitmap);
            }
        });
    }


    @Override
    public void onSocketConnected(boolean isHost) {
        if (!isAdded()) return;

        p2pConnected = true;

        requireActivity().runOnUiThread(() -> {
            if (isHost) {
                startServer.setText("Client Connected");
                startServer.setEnabled(false);
                Toast.makeText(requireContext(), "Server socket connected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onError(String error) {
        p2pConnected = false;

        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            startServer.setEnabled(true);
            startServer.setText("Start Server");
        });
    }
}