package com.example.airsimapp;

import android.util.Log;

import java.util.Calendar;

public class HeadingAndSpeed extends AutopilotCommand{
    private float desiredHeading;
    private float desiredSpeed;

    public HeadingAndSpeed(float heading, float speed, int hour, int minute){
        this.setId("Heading&Speed");
        this.desiredHeading = heading;
        this.desiredSpeed = speed;
        this.setHourEndTime(hour);
        this.setMinuteEndTime(minute);
    }

    public float getDesiredHeading() {
        return desiredHeading;
    }

    public float getDesiredSpeed() {
        return desiredSpeed;
    }

    public void calculateCommand(float currentHeading, float yawRate, float commandTime, Calendar currentTime){
        float lower = ((desiredHeading-this.getHeadingTolerance())%360);
        float upper = ((desiredHeading+this.getHeadingTolerance())%360);
        if(currentHeading >= upper || currentHeading <= lower){
            float distanceToRight = (currentHeading - desiredHeading + 360) % 360;
            float distanceToLeft = (desiredHeading - currentHeading + 360) % 360;
            if(distanceToRight > distanceToLeft || distanceToRight == distanceToLeft){
                //Turning right
                this.setCommandMessage("autopilot,right_turn," + yawRate + "," + desiredSpeed + "," + commandTime);
            }
            else if(distanceToRight < distanceToLeft){
                //Turning left
                this.setCommandMessage("autopilot,left_turn," + yawRate + "," + desiredSpeed + "," + commandTime);
            }
        }
        else{
            this.setCommandMessage("autopilot,forward," + yawRate + "," + desiredSpeed + "," + commandTime);
            currentTime = Calendar.getInstance();
            int currentHour = currentTime.get(Calendar.HOUR_OF_DAY);
            int currentMinute = currentTime.get(Calendar.MINUTE);
            Log.d("Heading&Speed", String.valueOf(currentHour));
            Log.d("Heading&Speed", String.valueOf(currentMinute));
            if (getHourEndTime() == currentHour && getMinuteEndTime() == currentMinute){
                this.setCommandMessage("autopilot,stop," + yawRate + "," + desiredSpeed + "," + commandTime);
                setCommandComplete(true);
            }
        }
    }
}