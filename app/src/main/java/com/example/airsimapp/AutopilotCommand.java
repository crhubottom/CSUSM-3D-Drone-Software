package com.example.airsimapp;

abstract public class AutopilotCommand {
    private String id;
    private int hourEndTime;
    private int minuteEndTime;
    private String commandMessage;
    private float headingTolerance;
    private float gpsTolerance;
    private float altitudeTolerance;
    private boolean commandComplete;


    public AutopilotCommand(){
        this.id = "NULL";
        this.hourEndTime = 0;
        this.minuteEndTime = 0;
        this.headingTolerance = 3;
        this.gpsTolerance = 0.00008983f;    //About 10 meters tolerance at the equator, used for latitude & longitude
        this.altitudeTolerance = 2;        //Tolerance of altitude in meters
        this.commandComplete = false;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void calculateCommand(){
        //Generic calculateCommand function
    }

    public void setHourEndTime(int hourEndTime) {
        this.hourEndTime = hourEndTime;
    }

    public void setMinuteEndTime(int minuteEndTime) {
        this.minuteEndTime = minuteEndTime;
    }

    public int getHourEndTime() {
        return hourEndTime;
    }

    public int getMinuteEndTime() {
        return minuteEndTime;
    }

    public boolean getCommandComplete() {
        return commandComplete;
    }

    public void setCommandComplete(boolean commandComplete) {
        this.commandComplete = commandComplete;
    }

    public String getCommandMessage() {
        return commandMessage;
    }

    public void setCommandMessage(String commandMessage) {
        this.commandMessage = commandMessage;
    }

    public float getHeadingTolerance() {
        return headingTolerance;
    }

    public float getAltitudeTolerance() {
        return altitudeTolerance;
    }

    public float getGpsTolerance() {
        return gpsTolerance;
    }
}
