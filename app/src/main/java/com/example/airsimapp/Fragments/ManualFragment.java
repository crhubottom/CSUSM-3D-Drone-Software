package com.example.airsimapp.Fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.airsimapp.Activities.StartupActivity;
import com.example.airsimapp.Activities.UserActivity;
import com.example.airsimapp.JoystickView;
import com.example.airsimapp.PixhawkMavlinkUsb;
import com.example.airsimapp.R;
import com.example.airsimapp.WebSocketClientTesting;

import java.util.Calendar;
import java.util.Date;

public class ManualFragment extends Fragment {

    private static final String TAG = "ManualFragment";

    private PixhawkMavlinkUsb pixhawk;
    private ImageView remoteView;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_manual, container, false);

        Button armButton = rootView.findViewById(R.id.TakeoffLanding);
        armButton.setBackgroundColor(Color.parseColor("#32CD32"));
        armButton.setText("ARM");

        armButton.setOnClickListener(v -> {
            if (!pixhawk.isArmed()) {
                pixhawk.arm();
            } else {
                pixhawk.setThrottle(0);
                pixhawk.disarm();
            }
        });

// Poll actual arm state at 4Hz to keep button in sync
        handler.post(new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                boolean armed = pixhawk.isArmed();
                armButton.setBackgroundColor(armed
                        ? Color.parseColor("#B22222")
                        : Color.parseColor("#32CD32"));
                armButton.setText(armed ? "DISARM" : "ARM");
                handler.postDelayed(this, 250);
            }
        });

        Button manualButton = rootView.findViewById(R.id.backButton2);
        manualButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), StartupActivity.class))
        );

        ImageButton autoPilotButton = rootView.findViewById(R.id.menuButton);
        autoPilotButton.setOnClickListener(v -> {
            if (getActivity() instanceof UserActivity) {
                ((UserActivity) getActivity())
                        .switchFragment(UserActivity.getAutopilotFragment());
            }
        });

        remoteView = rootView.findViewById(R.id.remoteCameraView);

        JoystickView joystickLeft = rootView.findViewById(R.id.joystick2);
        JoystickView joystickRight = rootView.findViewById(R.id.joystick);

        joystickLeft.setJoystickListener((angle, strength) -> {
            int[] xy = polarToXY(angle, strength, 5); // deadzone=5
            pixhawk.setYaw(xy[0]);                    // left/right
            pixhawk.setThrottle(stickYToThrottle(xy[1])); // up/down -> throttle
        });

        joystickRight.setJoystickListener((angle, strength) -> {
            int[] xy = polarToXY(angle, strength, 5);
            pixhawk.setRoll(xy[0]);           // right stick left/right
            pixhawk.setPitch(-xy[1]);         // ✅ invert so up = forward
        });
        return rootView;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pixhawk = new PixhawkMavlinkUsb(requireContext());
    }

    static int stickYToThrottle(int y) {
        // y is -1000..1000 from joystick, map only positive range to throttle
        return clamp(y, 0, 1000);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int[] polarToXY(double angleDeg, double strength, int deadzone) {
        if (strength < deadzone) strength = 0;

        double mag = clamp((int) Math.round(strength), 0, 100) / 100.0;
        double rad = Math.toRadians(-angleDeg);

        int x = (int) Math.round(Math.cos(rad) * mag * 1000.0);
        int y = (int) Math.round(Math.sin(rad) * mag * 1000.0);

        return new int[]{clamp(x, -1000, 1000), clamp(y, -1000, 1000)};
    }
    @Override
    public void onResume() {
        super.onResume();
        pixhawk.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        pixhawk.close();
    }


    private final WebSocketClientTesting.WebSocketImageListener imageListener =
            bitmap -> requireActivity().runOnUiThread(() -> {
                if (bitmap != null) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(180);
                    Bitmap rotated = Bitmap.createBitmap(
                            bitmap, 0, 0,
                            bitmap.getWidth(),
                            bitmap.getHeight(),
                            matrix, true
                    );
                    remoteView.setImageBitmap(rotated);
                }
            });
}