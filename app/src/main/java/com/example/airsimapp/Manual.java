package com.example.airsimapp;

public class Manual {
    public String translateCommand(String userAction, float yawRate, float velocity, float commandTime) {
        String action = "";
        switch (userAction) {
            case "manual,forward":
                action = "forward,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,backward":
                action = "backward,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,left":
                action = "left,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,right":
                action = "right,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,forward_left":
                action = "forward_left"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,forward_right":
                action = "forward_right"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,backward_left":
                action = "backward_left"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,backward_right":
                action = "backward_right,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,up":
                action = "up,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,down":
                action = "down," + yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,forward_up":
                action = "forward_up";
                break;
            case "manual,backward_up":
                action = "backward_up";
                break;
            case "manual,left_up":
                action = "left_up";
                break;
            case "manual,right_up":
                action = "right_up";
                break;
            case "manual,forward_left_up":
                action = "forward_left_up";
                break;
            case "manual,forward_right_up":
                action = "forward_right_up";
                break;
            case "manual,backward_left_up":
                action = "backward_left_up";
                break;
            case "manual,backward_right_up":
                action = "backward_right_up";
                break;
            case "manual,forward_down":
                action = "forward_down";
                break;
            case "manual,backward_down":
                action = "backward_down";
                break;
            case "manual,left_down":
                action = "left_down";
                break;
            case "manual,right_down":
                action = "right_down";
                break;
            case "manual,forward_left_down":
                action = "forward_left_down";
                break;
            case "manual,forward_right_down":
                action = "forward_right_down";
                break;
            case "manual,backward_left_down":
                action = "backward_left_down";
                break;
            case "manual,backward_right_down":
                action = "backward_right_down";
                break;
            case "manual,right_turn":
                action = "right_turn,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,left_turn":
                action = "left_turn,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,takeoff":
                action = "takeoff," + yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,land":
                action = "land,"+ yawRate + "," + velocity + "," + commandTime;
                break;
            case "manual,stop":
                action = "stop,"+ yawRate + "," + velocity + "," + commandTime;;
                break;
            default:
                action = "unknown";
        }

        try {
            return action;
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }
}