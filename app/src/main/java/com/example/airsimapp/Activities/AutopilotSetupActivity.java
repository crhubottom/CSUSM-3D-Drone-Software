package com.example.airsimapp.Activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.airsimapp.InstructionAdapter;
import com.example.airsimapp.InstructionItem;
import com.example.airsimapp.R;

import java.util.ArrayList;

public class AutopilotSetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getSupportActionBar().hide();
        setContentView(R.layout.fragment_autopilot);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button backButtonAutopilot = findViewById(R.id.backButtonAutopilot);
        backButtonAutopilot.setOnClickListener(v -> {
            Intent intent = new Intent(AutopilotSetupActivity.this, ManualControlActivity.class);
            startActivity(intent);
        });

        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(AutopilotSetupActivity.this, AutopilotActivity.class);
            startActivity(intent);
        });

        String[] items = {"Pattern 1", "Pattern 2", "Pattern 3"};
        Spinner spinner = findViewById(R.id.patternSpinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, items
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ArrayList<InstructionItem> list = new ArrayList<>();
        InstructionAdapter instructionAdapter = new InstructionAdapter(list);

        recyclerView.setAdapter(instructionAdapter);

        EditText latText = findViewById(R.id.LatText);
        EditText longText = findViewById(R.id.LongText);
        EditText altText = findViewById(R.id.AltText);
        Button gpsButton = findViewById(R.id.GPSButton);

        gpsButton.setOnClickListener(v -> {
            String lat = latText.getText().toString().trim();
            String lon = longText.getText().toString().trim();
            String alt = altText.getText().toString().trim();

            if (lat.isEmpty() || lon.isEmpty() || alt.isEmpty()) {
                Toast.makeText(this, "Please fill in all GPS coordinates", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Confirm GPS Entry")
                    .setMessage("Latitude: " + lat + "\nLongitude: " + lon + "\nAltitude: " + alt)
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        Toast.makeText(this, "GPS Coordinates Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.gpsicon, "Waypoint", "Lat: " + lat + " Lon: " + lon + " Alt: " + alt));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        EditText direction = findViewById(R.id.directionInput);
        EditText speed = findViewById(R.id.speedInput);
        EditText time = findViewById(R.id.timeInput);
        Button headingButton = findViewById(R.id.headingButton);
        headingButton.setOnClickListener(v -> {
            String dir = direction.getText().toString().trim();
            String speedText = speed.getText().toString().trim();
            String t = time.getText().toString().trim();

            if(dir.isEmpty() ||speedText.isEmpty() || t.isEmpty()){
                Toast.makeText(this, "Please fill in all Heading/Speed instructions", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Heading/Speed Entry")
                    .setMessage("Direction: " + dir + "\nSpeed: " + speedText + "\nTime: " + t)
                    .setPositiveButton("Confirm", (dialog, which)->{
                        Toast.makeText(this, "Heading/Speed Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.directionicon, "Heading/Speed", "Direction: " + dir + " Speed: " + speedText + " Time: " + t));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        EditText timePattern = findViewById(R.id.patternTime);
        Button patternButton = findViewById(R.id.patternButton);

        patternButton.setOnClickListener(v -> {
            String pattern = spinner.getSelectedItem().toString();
            String timePatternText = timePattern.getText().toString().trim();

            if(pattern.isEmpty() || timePatternText.isEmpty()){
                Toast.makeText(this, "Please fill in all Pattern instructions", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Pattern Entry")
                    .setMessage("Pattern: " + pattern + "\nTime: " + timePatternText)
                    .setPositiveButton("Confirm", (dialog, which)->{
                        Toast.makeText(this, "Pattern Confirmed!", Toast.LENGTH_SHORT).show();
                        list.add(new InstructionItem(R.drawable.patternicon, pattern, "Time" + timePatternText));
                        instructionAdapter.notifyItemInserted(list.size() - 1);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}
