package com.example.airsimapp.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airsimapp.R;

public class StartupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_startup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button DronePhoneButton = findViewById(R.id.DronePhoneButton);
        Button UserPhoneButton = findViewById(R.id.UserPhoneButton);
        DronePhoneButton.setOnClickListener(v -> {
            Intent intent = new Intent(StartupActivity.this, DroneSetupActivity.class);
            startActivity(intent);
        });
        UserPhoneButton.setOnClickListener(v -> {
            Intent intent = new Intent(StartupActivity.this, ManualControlActivity.class);
            startActivity(intent);
        });

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(v ->  {
            finishAffinity();
        });

    }
}