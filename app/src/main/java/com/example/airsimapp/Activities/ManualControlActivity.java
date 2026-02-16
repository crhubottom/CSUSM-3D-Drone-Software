package com.example.airsimapp.Activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.airsimapp.JoystickView;
import com.example.airsimapp.R;

public class ManualControlActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final boolean[] landed = {false};
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getSupportActionBar().hide();
        setContentView(R.layout.fragment_manual);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        JoystickView joystick = findViewById(R.id.joystick);

        joystick.setJoystickListener(new JoystickView.JoystickListener() {
            @Override
            public void onMove(double angle, double strength) {
                // angle: direction in degrees (-180 to 180)
                // strength: distance from center (0–100)
                // Map these values to drone controls
            }
        });

        Button backButton2 = findViewById(R.id.backButton2);
        backButton2.setOnClickListener(v -> {
            Intent intent = new Intent(ManualControlActivity.this, StartupActivity.class);
            startActivity(intent);
        });

        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(ManualControlActivity.this, v);
            popup.getMenuInflater().inflate(R.menu.menu_popup, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_autopilot) {
                    Intent intent = new Intent(ManualControlActivity.this, AutopilotSetupActivity.class);
                    Toast.makeText(this, "Switching to Autopilot", Toast.LENGTH_SHORT).show();
                    startActivity(intent);
                    return true;
                } else {
                    return false;
                }
            });

            popup.show();
        });

        Button TakeoffLandingButton = findViewById(R.id.TakeoffLanding);
        TakeoffLandingButton.setOnClickListener(v -> {
            landed[0] = !landed[0];
            if(landed[0]){
                TakeoffLandingButton.setBackgroundColor(Color.parseColor("#B22222"));
                TakeoffLandingButton.setText("Land");


                //takeoff logic here
            }
            else{
                TakeoffLandingButton.setBackgroundColor(Color.parseColor("#32CD32"));
                TakeoffLandingButton.setText("Takeoff");
            }

        });

    }


}