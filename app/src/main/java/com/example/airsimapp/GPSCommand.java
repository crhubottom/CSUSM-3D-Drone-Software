package com.example.airsimapp;

import java.util.Calendar;

public class GPSCommand extends AutopilotCommand{
    private float latitude;
    private float longitude;
    private float altitude;

    public GPSCommand(float lat, float lon, float alt, int hour, int minute){
        this.setId("GPS");
        this.latitude = lat;
        this.longitude = lon;
        this.altitude = alt;
        this.setHourEndTime(hour);
        this.setMinuteEndTime(minute);
    }

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
    }

    public float getAltitude() {
        return altitude;
    }

    public void calculateCommand(GPS currentGPS, float currentHeading, float yawRate, float speed, float commandTime, Calendar startTime) {
        float longitudeDifference = getLongitude() - currentGPS.getLongitude();
        float latitudeDifference = getLatitude() - currentGPS.getLatitude();
        float altitudeDifference = getAltitude() - currentGPS.getAltitude();
        float degreeDifference = (float) Math.toDegrees(Math.atan2(longitudeDifference, latitudeDifference));
        if (degreeDifference < 0) {
            degreeDifference += 360;
        }
        //float desiredHeading = (currentHeading + degreeDifference) % 360;
        float desiredHeading = degreeDifference; // This may fix turning forever
        float lowerHeading = ((desiredHeading-this.getHeadingTolerance())%360);
        float upperHeading = ((desiredHeading+this.getHeadingTolerance())%360);

        if(currentHeading >= upperHeading || currentHeading <= lowerHeading){
            float distanceToRight = (currentHeading - desiredHeading + 360) % 360;
            float distanceToLeft = (desiredHeading - currentHeading + 360) % 360;
            if(distanceToRight > distanceToLeft || distanceToRight == distanceToLeft){
                //Turning right
                this.setCommandMessage("autopilot,right_turn," + yawRate + "," + speed + "," + commandTime);
            }
            else if(distanceToRight < distanceToLeft){
                //Turning left
                this.setCommandMessage("autopilot,left_turn," + yawRate + "," + speed + "," + commandTime);
            }
        }
        else if(currentGPS.getAltitude() >= altitude + this.getAltitudeTolerance() || currentGPS.getAltitude() <= altitude - this.getAltitudeTolerance()){
            if(altitudeDifference > 0){
                this.setCommandMessage("autopilot,up," + yawRate + "," + speed + "," + commandTime);
            }
            else if(altitudeDifference < 0){
                this.setCommandMessage("autopilot,down," + yawRate + "," + speed + "," + commandTime);
            }
        }
        else if(currentGPS.getLatitude() >= latitude + this.getGpsTolerance() || currentGPS.getLatitude() <= latitude - this.getGpsTolerance() || currentGPS.getLongitude() >= longitude + this.getGpsTolerance() || currentGPS.getLongitude() <= longitude - this.getGpsTolerance()){
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);

// Convert all time to minutes
            int currentTotalSeconds = (currentHour * 60 + currentMinute) * 60;
            int endTotalSeconds = (getHourEndTime() * 60 + getMinuteEndTime()) * 60;

            int remainingSeconds = endTotalSeconds - currentTotalSeconds;

// Prevent divide-by-zero and negative values
            if (remainingSeconds > 0) {
                // Estimate remaining distance (very rough)
                float latDiff = Math.abs(latitude - currentGPS.getLatitude());
                float lonDiff = Math.abs(longitude - currentGPS.getLongitude());
                float estimatedDistance = (float) Math.sqrt(latDiff * latDiff + lonDiff * lonDiff); // degrees
                // Optional: convert to meters if you want (1 degree ~ 111,000 meters)
                estimatedDistance = estimatedDistance * 111000;
                speed = estimatedDistance / (remainingSeconds - 45); // m/s if distance converted, else degrees/min to degrees/sec
            }
            this.setCommandMessage("autopilot,forward," + yawRate + "," + speed + "," + commandTime);
        }
        else if(currentGPS.getLatitude() <= latitude + this.getGpsTolerance() && currentGPS.getLatitude() >= latitude - this.getGpsTolerance() && currentGPS.getLongitude() <= longitude + this.getGpsTolerance() && currentGPS.getLongitude() >= longitude - this.getGpsTolerance()) {
            this.setCommandComplete(true);

        }
        }
    }
