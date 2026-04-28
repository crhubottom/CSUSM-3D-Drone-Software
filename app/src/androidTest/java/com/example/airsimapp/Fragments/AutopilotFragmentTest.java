package com.example.airsimapp.Fragments;
import static org.junit.Assert.assertEquals;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.FragmentActivity;

import com.example.airsimapp.R;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(Enclosed.class)
class AutopilotFragmentTest {

    @RunWith(RobolectricTestRunner.class)
    public static class AutopilotFragmentTest {

        @Test
        public void gpsButton_emptyFields_showsToast() {
            ActivityController<FragmentActivity> controller =
                    Robolectric.buildActivity(FragmentActivity.class).setup();
            FragmentActivity activity = controller.get();

            AutopilotFragment fragment = new AutopilotFragment();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .add(fragment, null)
                    .commitNow();

            View view = fragment.requireView();

            Button gpsButton = view.findViewById(R.id.GPSButton);

            gpsButton.performClick();
            Shadows.shadowOf(activity.getMainLooper()).idle();

            assertEquals(
                    "Please fill in all GPS coordinates",
                    ShadowToast.getTextOfLatestToast()
            );
        }

        @Test
        public void headingButton_emptyFields_showsToast() {
            ActivityController<FragmentActivity> controller =
                    Robolectric.buildActivity(FragmentActivity.class).setup();
            FragmentActivity activity = controller.get();

            AutopilotFragment fragment = new AutopilotFragment();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .add(fragment, null)
                    .commitNow();

            View view = fragment.requireView();

            Button headingButton = view.findViewById(R.id.headingButton);

            headingButton.performClick();
            Shadows.shadowOf(activity.getMainLooper()).idle();

            assertEquals(
                    "Please fill in all Heading/Speed instructions",
                    ShadowToast.getTextOfLatestToast()
            );
        }

        @Test
        public void patternButton_emptyFields_showsToast() {
            ActivityController<FragmentActivity> controller =
                    Robolectric.buildActivity(FragmentActivity.class).setup();
            FragmentActivity activity = controller.get();

            AutopilotFragment fragment = new AutopilotFragment();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .add(fragment, null)
                    .commitNow();

            View view = fragment.requireView();

            Button patternButton = view.findViewById(R.id.patternButton);

            patternButton.performClick();
            Shadows.shadowOf(activity.getMainLooper()).idle();

            assertEquals(
                    "Please fill in all Pattern instructions",
                    ShadowToast.getTextOfLatestToast()
            );
        }
    }
}