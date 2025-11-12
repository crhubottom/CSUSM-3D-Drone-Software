package com.example.airsimapp.Fragments;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.airsimapp.Activities.UserActivity;
import com.example.airsimapp.R;
import com.example.airsimapp.WebSocketClientTesting;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import okhttp3.Response;

public class ManualFragment extends Fragment  {
    private static final String TAG = "ManualFragment";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    //private TextView output;
    private ImageView remoteView;
    private ExecutorService cameraExecutor;
    private final Set<String> activeActions = new HashSet<>();
    private Runnable commandRunnable;
    //private Orchestrator orchestrator;

    // These help us loop the commands being sent
    private static final long COMMAND_INTERVAL = 100;
    private final Handler commandHandler = new Handler(Looper.getMainLooper());
    public Date date = Calendar.getInstance().getTime();
    public Calendar calendar = Calendar.getInstance();


    String currentTime;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_manual, container, false);

        Button start = rootView.findViewById(R.id.start);
        Button forward = rootView.findViewById(R.id.forward);
        Button backward = rootView.findViewById(R.id.backward);
        Button left = rootView.findViewById(R.id.left);
        Button right = rootView.findViewById(R.id.right);
        Button takeoff = rootView.findViewById(R.id.takeoff);
        Button land = rootView.findViewById(R.id.land);
        Button up = rootView.findViewById(R.id.go_up);
        Button down = rootView.findViewById(R.id.go_down);
        Button rleft = rootView.findViewById(R.id.Rleft);
        Button rright = rootView.findViewById(R.id.Rright);
        Button autoPilotButton = rootView.findViewById(R.id.autoPilotButton);
        remoteView = rootView.findViewById(R.id.remoteCameraView);
        WebSocketClientTesting socket = UserActivity.getOrchestrator().webSocket;
        TextView speedTextView = rootView.findViewById(R.id.speedTextView);
        TextView headingTextView = rootView.findViewById(R.id.HeadingViewText);
        TextView gpsTextView = rootView.findViewById(R.id.gpsTextView);
        socket.setWebSocketStateListener(new WebSocketClientTesting.WebSocketStateListener() {
            @Override
            public void onOpen() {
                // runs on main thread for you (because WebSocketClientTesting posts there)
                start.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.status_okt)
                );
                start.setText("WebSocket CONNECTED");
            }

            @Override
            public void onFailure(Throwable t, Response response) {
                start.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.button_primary)
                );
                start.setText("Start");
                Toast.makeText(requireContext(),
                        "Failed to connect: " + t.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        // flightControllerSpinner may need to be in dronePhoneFragment

        // Set up listeners, this is what the buttons do when clicked/held.
        autoPilotButton.setOnClickListener(v -> {
            // Ensure the activity is of type UserActivity
            if (getActivity() instanceof UserActivity) {
                // Call switchFragment on the activity
                ((UserActivity) getActivity()).switchFragment(UserActivity.getAutopilotFragment());
            }
        });
        start.setOnClickListener(v -> UserActivity.getOrchestrator().connectToPhone());
        takeoff.setOnClickListener(v -> UserActivity.getOrchestrator().processCommand("manual,takeoff", this::sendCommand));
        land.setOnClickListener(v -> UserActivity.getOrchestrator().processCommand("manual,land", this::sendCommand));
        setMovementListener(forward, "manual,forward");
        setMovementListener(backward, "manual,backward");
        setMovementListener(left, "manual,left");
        setMovementListener(right, "manual,right");
        setMovementListener(up, "manual,up");
        setMovementListener(down, "manual,down");
        setMovementListener(rleft, "manual,left_turn");
        setMovementListener(rright, "manual,right_turn");

//        if (allPermissionsGranted()) {
//            startCamera();
//        } else {
//            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
//        }
        return rootView;


    }
    private void setMovementListener(Button button, String action) {
        button.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!activeActions.contains(action)) {
                        activeActions.add(action);
                        startCommandLoop(); // Start sending commands continuously

                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activeActions.remove(action);
                    if (activeActions.isEmpty()) {
                        stopCommandLoop(); // Stop sending commands if no buttons are held
                    }
                    break;
            }
            return true;
        });
    }
    private void updateAndSendCommand() {
        if (activeActions.isEmpty()) {
            UserActivity.getOrchestrator().processCommand("manual,stop", ManualFragment.this::sendCommand);
        } else {
            // Define correct order of actions
            //String[] correctOrder = {"manual,forward", "manual,backward", "manual,left", "manual,right", "manual,up", "manual,down", "manual,left_turn", "manual,right_turn"};

            // Sort activeActions according to the predefined order
            List<String> sortedActions = new ArrayList<>(activeActions);
            //sortedActions.sort(Comparator.comparingInt(action -> Arrays.asList(correctOrder).indexOf(action)));
            // Combine active actions using underscores (e.g., "forward_right")
            String combinedAction = String.join("_", sortedActions);
            UserActivity.getOrchestrator().processCommand(combinedAction, ManualFragment.this::sendCommand);
        }
    }
    private void startCommandLoop() {
        if (commandRunnable == null) {
            commandRunnable = new Runnable() {
                @Override
                public void run() {
                    updateAndSendCommand(); // Send the movement command
                    commandHandler.postDelayed(this, COMMAND_INTERVAL); // Repeat after delay
                }
            };
            commandHandler.post(commandRunnable); // Start the loop
        }
    }

    private void stopCommandLoop() {
        if (commandRunnable != null) {
            commandHandler.removeCallbacks(commandRunnable); // Stop sending commands
            commandRunnable = null;
            UserActivity.getOrchestrator().processCommand("manual,stop", ManualFragment.this::sendCommand); // Send stop command
        }
    }
    //    private void startCamera() {
//        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
//        cameraProviderFuture.addListener(() -> {
//            try {
//                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
//                CameraSelector cameraSelector = new CameraSelector.Builder()
//                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                        .build();
//
//                androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
//                preview.setSurfaceProvider(previewView.getSurfaceProvider());
//
//                Camera camera = cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview);
//            } catch (Exception e) {
//                Log.e(TAG, "Use case binding failed", e);
//            }
//        }, ContextCompat.getMainExecutor(requireContext()));
//    }
    private void sendCommand(String command) {
        calendar.setTime(date);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        currentTime = (Calendar.getInstance().getTime().toString());
        Log.d(TAG, "Sending command: " + command);
        Log.e(TAG, "Current Time: " + hour+  " , " + minute);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCommandLoop();
        //cameraExecutor.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        UserActivity.getOrchestrator().webSocket.addImageListener(imageListener);
//
//        // Re-register the Manual‐fragment listener
//        UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(
//                new WebSocketClientTesting.WebSocketMessageListener() {
//                    @Override
//                    public void onMessageReceived(String msg) { /* … */ }
//
//                    @Override
//                    public void onByteReceived(Bitmap bitmap) {
//                        requireActivity().runOnUiThread(() -> {
//                            if (bitmap != null) {
//                                // Rotate the bitmap 90 degrees
//                                Matrix matrix = new Matrix();
//                                matrix.postRotate(180); // or -90 depending on your camera orientation
//                                Bitmap rotatedBitmap = Bitmap.createBitmap(
//                                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
//                                );
//
//                                remoteView.setImageBitmap(rotatedBitmap);
//                            }
//                        });
//                    }
//                }
//        );
    }

    @Override
    public void onPause() {
        super.onPause();
        // Optionally clear it so you don’t leak or double-fire:
        UserActivity.getOrchestrator().webSocket.removeImageListener(imageListener);
    }

    private final WebSocketClientTesting.WebSocketImageListener imageListener = bitmap -> {
        requireActivity().runOnUiThread(() -> {
            if (bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(180);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                remoteView.setImageBitmap(rotatedBitmap);
            }
        });
    };

}