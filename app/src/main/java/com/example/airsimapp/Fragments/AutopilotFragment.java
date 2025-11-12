package com.example.airsimapp.Fragments;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.airsimapp.Activities.UserActivity;
import com.example.airsimapp.AutopilotCommand;
import com.example.airsimapp.CommandAdapter;
import com.example.airsimapp.GPS;
import com.example.airsimapp.GPSCommand;
import com.example.airsimapp.HeadingAndSpeed;
import com.example.airsimapp.LoiterPattern;
import com.example.airsimapp.R;
import com.example.airsimapp.WebSocketClientTesting;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class AutopilotFragment extends Fragment {
    private static final String TAG = "AutopilotFragment";
    private TextView speedTextView;
    private TextView headingTextView;
    private ImageView remoteView;
    private RecyclerView commandRecyclerView;
    private CommandAdapter commandAdapter;
    private TextView gpsTextView;
    private double currentSpeed = 0.0;
    private float currentHeading = 0.0F;
    private Runnable uiUpdateRunnable;
    public GPS currentGPS;
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    Handler handler = new Handler();
    int index = 0;
    private static final long UPDATE_INTERVAL = 100;
    public Date date = Calendar.getInstance().getTime();
    public Calendar calendar = Calendar.getInstance();
    private String selectedPattern = "Circle"; // default

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_autopilot, container, false);


        commandRecyclerView = view.findViewById(R.id.commandRecyclerView);
        commandRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        commandAdapter = new CommandAdapter(UserActivity.getOrchestrator().getAutopilot().getCommandQueue(),
                position -> {
                    UserActivity.getOrchestrator().getAutopilot().getCommandQueue().remove(position);
                    commandAdapter.notifyItemRemoved(position);
                }
        );
        commandRecyclerView.setAdapter(commandAdapter);
        // Get the button from the layout
        Button manualButton = view.findViewById(R.id.manualButton);
        EditText latitude = view.findViewById(R.id.gpsCord);
        EditText longitude = view.findViewById(R.id.gpsCord2);
        EditText altitude = view.findViewById(R.id.gpsCord3);
        EditText gpsTime = view.findViewById(R.id.gpsTime);
        EditText heading = view.findViewById(R.id.heading);
        EditText speed = view.findViewById(R.id.Speed);
        EditText headingTime = view.findViewById(R.id.headingTime);
        EditText patternTime = view.findViewById(R.id.patternTime);
        Button addGPS = view.findViewById(R.id.addGPS);
        Button addHeadingSpeed = view.findViewById(R.id.addHeadingSpeed);
        Button addPattern = view.findViewById(R.id.addPattern);
        Button startButton = view.findViewById(R.id.startautoflight);
        remoteView = view.findViewById(R.id.autopilotPreviewView);
        Spinner patternSpinner = view.findViewById(R.id.pattern);
        String[] patternOptions = {"RaceTrack", "Circle", "Figure8"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, patternOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        patternSpinner.setAdapter(spinnerAdapter);

        patternSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPattern = patternOptions[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPattern = "RaceTrack";
            }
        });

        startButton.setOnClickListener(v -> {
            handler.post(commandSenderRunnable); // start sending
        });



        addHeadingSpeed.setOnClickListener(v -> {
            if (heading.getText().toString().isEmpty() || speed.getText().toString().isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in heading and speed", Toast.LENGTH_SHORT).show();
            } else {
                String headingStr = heading.getText().toString().trim();
                String speedStr = speed.getText().toString().trim();
                String timeStr = headingTime.getText().toString().trim();

                // If time is optional, we can pass default
                if (timeStr.isEmpty()) {
                    UserActivity.getOrchestrator().getAutopilot().addToCommandQueue(headingStr, speedStr, "0000");
                } else {
                    UserActivity.getOrchestrator().getAutopilot().addToCommandQueue(headingStr, speedStr, timeStr);
                }

                commandAdapter.updateCommands(UserActivity.getOrchestrator().getAutopilot().getCommandQueue());
                heading.setText("");
                speed.setText("");
                headingTime.setText("");
            }
            // This is for testing, is out queue being tracked correctly.
            for (int i = 0; i < UserActivity.getOrchestrator().getAutopilot().getCommandQueue().size(); i++) {
               //log .e(TAG, UserActivity.getOrchestrator().getAutopilot().getCommandQueue().get(i).getId());
            }
        });
        addGPS.setOnClickListener(v -> {
            if (latitude.getText().toString().isEmpty() || longitude.getText().toString().isEmpty() || altitude.getText().toString().isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in latitude, longitude, and altitude", Toast.LENGTH_SHORT).show();
            } else {
                String latStr = latitude.getText().toString().trim();
                String lonStr = longitude.getText().toString().trim();
                String altStr = altitude.getText().toString().trim();
                String gpsTimeStr = gpsTime.getText().toString().trim();

                if (gpsTimeStr.isEmpty()) {
                    UserActivity.getOrchestrator().getAutopilot().addToCommandQueue(latStr, lonStr, altStr, "0000");
                } else {
                    UserActivity.getOrchestrator().getAutopilot().addToCommandQueue(latStr, lonStr, altStr, gpsTimeStr);
                }

                commandAdapter.updateCommands(UserActivity.getOrchestrator().getAutopilot().getCommandQueue());
                latitude.setText("");
                longitude.setText("");
                altitude.setText("");
                gpsTime.setText("");
            }

            for (int i = 0; i < UserActivity.getOrchestrator().getAutopilot().getCommandQueue().size(); i++) {
               // Log.e(TAG, UserActivity.getOrchestrator().getAutopilot().getCommandQueue().get(i).getId());
            }
        });
        addPattern.setOnClickListener(v -> {
            if (patternTime.getText().toString().isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
            } else {
                float patternYaw = UserActivity.getOrchestrator().getAutopilot().getYawRate();
                float patternSpeed = UserActivity.getOrchestrator().getAutopilot().getVelocity();
                commandAdapter.updateCommands(UserActivity.getOrchestrator().getAutopilot().getCommandQueue());
                UserActivity.getOrchestrator().getAutopilot().addToCommandQueue(selectedPattern, patternYaw, patternSpeed, patternTime.getText().toString().trim());
                patternTime.setText("");
            }
            for (int i = 0; i < UserActivity.getOrchestrator().getAutopilot().getCommandQueue().size(); i++) {
              //  Log.e(TAG, UserActivity.getOrchestrator().getAutopilot().getCommandQueue().get(i).getId());
            }
        });


        // Set the button's click listener to return to the UserPhoneFragment
        manualButton.setOnClickListener(v -> {
            // Ensure the activity is of type UserActivity
            if (getActivity() instanceof UserActivity) {
                // Call switchFragment on the activity
                ((UserActivity) getActivity()).switchFragment(UserActivity.getUserPhoneFragment());
            }
        });



        // This will listen for messages from the drone websocket
//        UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(message -> {
//            String[] strBreakup = message.split(",");
//            if (Objects.equals(strBreakup[0], "getSpeed")) {
//                UserActivity.getOrchestrator().getAutopilot().setCurrentSpeed(Float.parseFloat(strBreakup[1]));
//               // Log.d(TAG, "TESTING speed: " + UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());
//            } else if (Objects.equals(strBreakup[0], "getHeading")) {
//                UserActivity.getOrchestrator().getAutopilot().setCurrentHeading(Float.parseFloat(strBreakup[1]));
//               // Log.d(TAG, "TESTING heading: " + UserActivity.getOrchestrator().getAutopilot().getCurrentHeading());
//            } else if (Objects.equals(strBreakup[0], "getGPS")){
//                currentGPS = new GPS(strBreakup[1], strBreakup[2], strBreakup[3]);
//                UserActivity.getOrchestrator().getAutopilot().setCurrentGPS(currentGPS);
//               // Log.d(TAG, "TESTING gps: " + UserActivity.getOrchestrator().getAutopilot().getCurrentGPS());
//            }
//        });


        return view;
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get a reference to the TextView
        speedTextView = view.findViewById(R.id.speedTextView);
        headingTextView = view.findViewById(R.id.HeadingViewText);
        gpsTextView = view.findViewById(R.id.gpsTextView);
        startSpeedUpdates();
    }

    private void updateUI() {
        String speedText = getString(R.string.speed_display, getCurrentSpeed());
        speedTextView.setText(speedText);
        //update heading
        String headingText = getString(R.string.heading_display, getCurrentHeading());
        headingTextView.setText(headingText);

        //update GPS
        if(currentGPS != null)
        {
            String gpsText = getString(R.string.gps_display, getCurrentGPS().getLatitude(), getCurrentGPS().getLongitude(), getCurrentGPS().getAltitude());
            gpsTextView.setText(gpsText);
        }
    }
    private double getCurrentSpeed() {
        return (UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());
    }
    private GPS getCurrentGPS() {
        return (UserActivity.getOrchestrator().getAutopilot().getCurrentGPS());
    }

    private float getCurrentHeading() {
        return (UserActivity.getOrchestrator().getAutopilot().getCurrentHeading());
    }
    private void startSpeedUpdates() {
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
//                UserActivity.getOrchestrator().webSocket.sendMessage("getGPS");
//                UserActivity.getOrchestrator().webSocket.sendMessage("getSpeed");
//                UserActivity.getOrchestrator().webSocket.sendMessage("getHeading");
                UserActivity.getOrchestrator().processCommand("getGPS", AutopilotFragment.this::sendCommand);
                UserActivity.getOrchestrator().processCommand("getSpeed", AutopilotFragment.this::sendCommand);
                UserActivity.getOrchestrator().processCommand("getHeading", AutopilotFragment.this::sendCommand);
                //Log.d(TAG, "THIS SHOULD BE SPEED: " + UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());

                currentSpeed = getCurrentSpeed();
                currentHeading = getCurrentHeading();
                currentGPS = getCurrentGPS();
                updateUI();
                uiUpdateHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        uiUpdateHandler.post(uiUpdateRunnable);
    }
    private void stopSpeedUpdates() {
        if(uiUpdateHandler != null && uiUpdateRunnable != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopSpeedUpdates();
    }

    Runnable commandSenderRunnable = new Runnable() {
        @Override
        public void run() {
            List<AutopilotCommand> queue = UserActivity.getOrchestrator().getAutopilot().getCommandQueue();

            if (queue.isEmpty()) {
                handler.removeCallbacks(this); // Stop the loop
                return;
            }
            AutopilotCommand command = queue.get(0);

                //AutopilotCommand command = queue.get(0);
                if (command.getCommandComplete()){
                    UserActivity.getOrchestrator().getAutopilot().getCommandQueue().remove(command);
                    requireActivity().runOnUiThread(() -> {
                        commandAdapter.notifyDataSetChanged(); // Updates the command queue if command is complete
                    });

                    if (!queue.isEmpty()) {
                        handler.postDelayed(this, 100);
                    }
                    return;
                } else {

                // Recalculate command before sending
                if (command instanceof HeadingAndSpeed) {

                    ((HeadingAndSpeed) command).calculateCommand(
                            UserActivity.getOrchestrator().getAutopilot().getCurrentHeading(),
                            UserActivity.getOrchestrator().getAutopilot().getYawRate(),
                            UserActivity.getOrchestrator().getAutopilot().getCommandTime(),
                            calendar
                    );
                } else if (command instanceof GPSCommand) {
                    ((GPSCommand) command).calculateCommand(UserActivity.getOrchestrator().getAutopilot().getCurrentGPS(),
                            UserActivity.getOrchestrator().getAutopilot().getCurrentHeading(),
                            UserActivity.getOrchestrator().getAutopilot().getYawRate(),
                            UserActivity.getOrchestrator().getAutopilot().getVelocity(),
                            UserActivity.getOrchestrator().getAutopilot().getCommandTime(),
                            calendar);
                } else if (command instanceof LoiterPattern) {
                            ((LoiterPattern) command).calculateCommand(
                                    UserActivity.getOrchestrator().getAutopilot().getCurrentHeading(),
                                    UserActivity.getOrchestrator().getAutopilot().getYawRate(),
                                    UserActivity.getOrchestrator().getAutopilot().getVelocity(),
                                    UserActivity.getOrchestrator().getAutopilot().getCommandTime(),
                                    calendar
                            );
                }
                    String msg = command.getCommandMessage();
                    if (msg != null && !msg.isEmpty()) {
                        UserActivity.getOrchestrator().processCommand(msg, AutopilotFragment.this::sendCommand);
                       // Log.d(TAG, "Sent: " + msg);
                    }
                handler.postDelayed(this, 100); // Repeat every 100ms (10 times per second)
                    }
                }
    };

    private void sendCommand(String s) {
        //Log.d(TAG, "Sending autopilot command");
    }

    @Override
    public void onResume() {
        super.onResume();
        UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(new WebSocketClientTesting.WebSocketMessageListener() {
            @Override
            public void onMessageReceived(String msg) {
                String[] strBreakup = msg.split(",");
                if (Objects.equals(strBreakup[0], "getSpeed")) {
                    UserActivity.getOrchestrator().getAutopilot().setCurrentSpeed(Float.parseFloat(strBreakup[1]));
                    // Log.d(TAG, "TESTING speed: " + UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());
                } else if (Objects.equals(strBreakup[0], "getHeading")) {
                    UserActivity.getOrchestrator().getAutopilot().setCurrentHeading(Float.parseFloat(strBreakup[1]));
                    // Log.d(TAG, "TESTING heading: " + UserActivity.getOrchestrator().getAutopilot().getCurrentHeading());
                } else if (Objects.equals(strBreakup[0], "getGPS")){
                    currentGPS = new GPS(strBreakup[1], strBreakup[2], strBreakup[3]);
                    UserActivity.getOrchestrator().getAutopilot().setCurrentGPS(currentGPS);
                    // Log.d(TAG, "TESTING gps: " + UserActivity.getOrchestrator().getAutopilot().getCurrentGPS());
                }
            }

            @Override
            public void onByteReceived(Bitmap bitmap) {
                // run on UI thread and paint into the ImageView
//                requireActivity().runOnUiThread(() -> {
//
//                    remoteView.setImageBitmap(bitmap);
//                });
            }
        });
        startSpeedUpdates();  // start updates when fragment is visible again
    }

    @Override
    public void onPause() {
        super.onPause();
        stopSpeedUpdates();   // stop updates when fragment is no longer visible
        UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(null);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // this fragment is now visible
            UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(new WebSocketClientTesting.WebSocketMessageListener() {
                @Override
                public void onMessageReceived(String msg) {
                    String[] strBreakup = msg.split(",");
                    if (Objects.equals(strBreakup[0], "getSpeed")) {
                        UserActivity.getOrchestrator().getAutopilot().setCurrentSpeed(Float.parseFloat(strBreakup[1]));
                        // Log.d(TAG, "TESTING speed: " + UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());
                    } else if (Objects.equals(strBreakup[0], "getHeading")) {
                        UserActivity.getOrchestrator().getAutopilot().setCurrentHeading(Float.parseFloat(strBreakup[1]));
                        // Log.d(TAG, "TESTING heading: " + UserActivity.getOrchestrator().getAutopilot().getCurrentHeading());
                    } else if (Objects.equals(strBreakup[0], "getGPS")){
                        currentGPS = new GPS(strBreakup[1], strBreakup[2], strBreakup[3]);
                        UserActivity.getOrchestrator().getAutopilot().setCurrentGPS(currentGPS);
                        // Log.d(TAG, "TESTING gps: " + UserActivity.getOrchestrator().getAutopilot().getCurrentGPS());
                    }
                }

                @Override
                public void onByteReceived(Bitmap bitmap) {
                    // run on UI thread and paint into the ImageView
                    requireActivity().runOnUiThread(() -> {
                        if (bitmap != null) {
                            // Rotate the bitmap 90 degrees
                            Matrix matrix = new Matrix();
                            matrix.postRotate(180); // or -90 depending on your camera orientation
                            Bitmap rotatedBitmap = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                            );

                            remoteView.setImageBitmap(rotatedBitmap);
                        }
                    });
                }
            });
            startSpeedUpdates();
        } else {
            // fragment is now hidden
            UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(null);
            stopSpeedUpdates();
        }
    }
}