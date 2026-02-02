package com.example.droneflightuiprototype;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DroneSetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_drone_setup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Spinner spinner = findViewById(R.id.FlightControllerChoice);
        String[] FlightControllerOptions = {"AirSim Controller", "DroneFlight Controller"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, FlightControllerOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        Button PairingButton = findViewById(R.id.PairingButton);
        PairingButton.setOnClickListener(v -> {
            String selectedController = spinner.getSelectedItem().toString();
            if(pairDevices(selectedController)){
                new AlertDialog.Builder(DroneSetupActivity.this).setTitle("Pairing Successful!").setMessage("Your device is now paired with " + selectedController + ".").setPositiveButton("Continue", ((dialog, which) -> {
                    Intent intent = new Intent(DroneSetupActivity.this, DroneDuringFlight.class);
                    startActivity(intent);
                }))
                        .show();

            }
            else{
                new AlertDialog.Builder(DroneSetupActivity.this).setTitle("Pairing Failed.").setMessage("Unable to pair with " + selectedController + ".").setPositiveButton("OK", null).show();
            }
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(DroneSetupActivity.this, StartupActivity.class);
            startActivity(intent);
        });


    }

    private boolean pairDevices(String FlightController){
        //Call pre-existing pairing logic
        return true;
    }

}


