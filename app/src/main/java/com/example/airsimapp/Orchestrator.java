package com.example.airsimapp;

import android.util.Log;

public class Orchestrator {

    private Autopilot autopilot;
    public WebSocketClientTesting webSocket;
    //private final flightControllerInterface flightController;
    private String command;
    private boolean isConnected = false;

    public Orchestrator(flightControllerInterface flightController) {
        //this.flightController = flightController;
        this.webSocket = new WebSocketClientTesting();
        this.autopilot = new Autopilot();
    }

    public Orchestrator() {
        //this.flightController = flightController;
        this.webSocket = new WebSocketClientTesting();
        this.autopilot = new Autopilot();
    }

    public Autopilot getAutopilot() {
        return autopilot;
    }

    public void connectToPhone() {
        if (!isConnected) {
            webSocket.connect("ws://192.168.1.242:8766");
            isConnected = true;
        }
//        webSocket.setWebSocketMessageListener(new WebSocketClientTesting.WebSocketMessageListener() {
//            @Override
//            public void onMessageReceived(String message) {
//                UserActivity.getAutopilotFragment().onFlightControllerMessage(message);
//            }
//        });
       // flightController.connect();
    } // This can connect to websockets instead of drone directly

    public void processCommand(String userAction, CommandCallback callback) {
        String[] message = userAction.split(",");
        switch(message[0]){ //Use action identifier for each type of message
            case "manual":
                command = autopilot.getManual().translateCommand(userAction, autopilot.getYawRate(), autopilot.getVelocity(), autopilot.getCommandTime());
                callback.onCommandReady(command);
                webSocket.sendMessage(command); // Send to websocket -> Drone Phone
                break;
            case "autopilot":
                StringBuilder autopilotCommand = new StringBuilder();
                for (int i = 1; i < message.length; i++) {
                    autopilotCommand.append(message[i]);
                    if (i != message.length - 1) {
                        autopilotCommand.append(",");
                    }
                }

                String commandStr = autopilotCommand.toString();
                callback.onCommandReady(commandStr);
                webSocket.sendMessage(commandStr);
                break;
            case "getGPS":
            case "getSpeed":
            case "getHeading":
                webSocket.sendMessage(userAction);
                break;
            default:
                Log.e("Orchestrator", "Unknown Message Received, Cannot Process Command");
        }


    }

    public interface CommandCallback {
        void onCommandReady(String jsonCommand);
    }
}
