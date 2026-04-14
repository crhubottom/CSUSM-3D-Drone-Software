package com.example.airsimapp.Fragments;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.airsimapp.Activities.UserActivity;
import com.example.airsimapp.Autopilot;
import com.example.airsimapp.AutopilotCommand;
import com.example.airsimapp.CommandAdapter;
import com.example.airsimapp.GPS;
import com.example.airsimapp.GPSCommand;
import com.example.airsimapp.HeadingAndSpeed;
import com.example.airsimapp.InstructionAdapter;
import com.example.airsimapp.InstructionItem;
import com.example.airsimapp.LoiterPattern;
import com.example.airsimapp.R;

import java.util.ArrayList;
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

    Autopilot autopilot;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_autopilot, container, false);
        Button manualButton = view.findViewById(R.id.backButtonAutopilot);
        // Set the button's click listener to return to the UserPhoneFragment
        manualButton.setOnClickListener(v -> {
            // Ensure the activity is of type UserActivity
            if (getActivity() instanceof UserActivity) {
                // Call switchFragment on the activity
                ((UserActivity) getActivity()).switchFragment(UserActivity.getUserPhoneFragment());
            }
        });
        String[] items = {"RaceTrack", "Circle", "Figure8"};
        Spinner spinner = view.findViewById(R.id.patternSpinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, items
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));


        ArrayList<InstructionItem> list = new ArrayList<>();
        InstructionAdapter instructionAdapter = new InstructionAdapter(list);
        recyclerView.setAdapter(instructionAdapter);
        EditText latText = view.findViewById(R.id.LatText);
        EditText longText = view.findViewById(R.id.LongText);
        EditText altText = view.findViewById(R.id.AltText);
        Button gpsButton = view.findViewById(R.id.GPSButton);

        gpsButton.setOnClickListener(v -> {
            Log.e("Autopilot", "GPS Triggered");
            autopilot = new Autopilot();
            String lat = latText.getText().toString().trim();
            String lon = longText.getText().toString().trim();
            String alt = altText.getText().toString().trim();

            if (lat.isEmpty() || lon.isEmpty() || alt.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all GPS coordinates", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle("Confirm GPS Entry")
                    .setMessage("Latitude: " + lat + "\nLongitude: " + lon + "\nAltitude: " + alt)
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        Toast.makeText(getContext(), "GPS Coordinates Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.gpsicon, "Waypoint", "Lat: " + lat + " Lon: " + lon + " Alt: " + alt));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            //add autopilot command to the Queue
            //Todo: fix time entry. As is, fixed at "1000". Need to translate instructions in to drone command
            autopilot.addToCommandQueue(lat, lon, alt, "1000");

        });

        EditText direction = view.findViewById(R.id.directionInput);
        EditText speed = view.findViewById(R.id.speedInput);
        EditText time = view.findViewById(R.id.timeInput);
        Button headingButton = view.findViewById(R.id.headingButton);
        headingButton.setOnClickListener(v -> {
            autopilot = new Autopilot();
            String dir = direction.getText().toString().trim();
            String speedText = speed.getText().toString().trim();
            String t = time.getText().toString().trim();

            if(dir.isEmpty() ||speedText.isEmpty() || t.isEmpty()){
                Toast.makeText(getContext(), "Please fill in all Heading/Speed instructions", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle("Confirm Heading/Speed Entry")
                    .setMessage("Direction: " + dir + "\nSpeed: " + speedText + "\nTime: " + t)
                    .setPositiveButton("Confirm", (dialog, which)->{
                        Toast.makeText(getContext(), "Heading/Speed Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.directionicon, "Heading/Speed", "Direction: " + dir + " Speed: " + speedText + " Time: " + t));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            Log.e("Autopilot", "Heading/Speed triggered");
            //add autopilot command to the Queue
            //Todo: Need to translate instructions into drone command.
            autopilot.addToCommandQueue(dir, speedText, t);
        });

        EditText timePattern = view.findViewById(R.id.patternTime);
        Button patternButton = view.findViewById(R.id.patternButton);

        patternButton.setOnClickListener(v -> {
            String pattern = spinner.getSelectedItem().toString();
            String timePatternText = timePattern.getText().toString().trim();
            autopilot = new Autopilot();

            if(pattern.isEmpty() || timePatternText.isEmpty()){
                Toast.makeText(getContext(), "Please fill in all Pattern instructions", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle("Confirm Pattern Entry")
                    .setMessage("Pattern: " + pattern + "\nTime: " + timePatternText)
                    .setPositiveButton("Confirm", (dialog, which)->{
                        Toast.makeText(getContext(), "Pattern Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.patternicon, pattern, "Time" + timePatternText));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            //add autopilot command to the Queue
            //Todo: fix yawRate, speed, and time entry. As is, fixed at 10, 10, and "1000". Also need to translate into drone commands. UI does not support any real patter, only "pattern 1, 2, 3" etc.
            //Todo: crashes when trying to add pattern to queue, likely cause "pattern 1, 2 or 3" does not exist
            Log.e("Autopilot", "Pattern triggered: " + pattern);
            autopilot.addToCommandQueue(pattern, 10, 10, "1000");
        });
        return view;
    }
    private double getCurrentSpeed() {
        return (UserActivity.getOrchestrator().getAutopilot().getCurrentSpeed());
    }
    private GPS getCurrentGPS() {
        return (UserActivity.getOrchestrator().getAutopilot().getCurrentGPS());
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
    }

    @Override
    public void onPause() {
        super.onPause();
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
        } else {
            // fragment is now hidden
            UserActivity.getOrchestrator().webSocket.setWebSocketMessageListener(null);
        }
    }


}