package com.example.airsimapp.Activities;

import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.airsimapp.Fragments.AutopilotFragment;
import com.example.airsimapp.Fragments.ManualFragment;
import com.example.airsimapp.Orchestrator;
import com.example.airsimapp.R;

public class UserActivity extends AppCompatActivity {
    private static Orchestrator orchestrator;
    private static ManualFragment manualFragment;
    private static AutopilotFragment autopilotFragment;
    private FragmentTransaction fragmentTransaction;

    public static Fragment getAutopilotFragment(){
        return autopilotFragment;
    }
    public static Fragment getUserPhoneFragment(){
        return manualFragment;
    }
    public static Orchestrator getOrchestrator() {
        return orchestrator;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        orchestrator = new Orchestrator();
        manualFragment = new ManualFragment();
        autopilotFragment = new AutopilotFragment();

        if (savedInstanceState == null) {
            fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.add(R.id.fragment_container, manualFragment);
            fragmentTransaction.add(R.id.fragment_container, autopilotFragment);
            fragmentTransaction.hide(autopilotFragment);
            fragmentTransaction.commit();
        }
    }

    public void switchFragment(Fragment newFragment) {
        fragmentTransaction = getSupportFragmentManager().beginTransaction();

        fragmentTransaction.show(newFragment);

        if (newFragment instanceof ManualFragment) {
            fragmentTransaction.hide(autopilotFragment);
        } else if (newFragment instanceof AutopilotFragment) {
            fragmentTransaction.hide(manualFragment);
        }

        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void hideSystemUI() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if(controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
}