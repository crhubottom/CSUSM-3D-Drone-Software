package com.example.airsimapp;

import android.util.Log;
import android.widget.TextView;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AirSimFlightController implements flightControllerInterface {
    private static final String TAG = "AirSimFlightController";
    private WebSocket webSocket;
    private MessageListener messageListener;
    private final OkHttpClient client;
    private final TextView output;
    private String Message;

    public AirSimFlightController(TextView output) {
        this.client = new OkHttpClient();
        this.output = output;
    }

    public AirSimFlightController(){
        this.client = new OkHttpClient();
        output = null;
    }

    @Override
    public void connect() {
        Request request = new Request.Builder().url("ws://192.168.1.242:8766").build();
        webSocket = client.newWebSocket(request, new EchoWebSocketListener());
    }

    @Override
    public void sendToDrone(String jsonCommand) {
        if (webSocket != null) {
            webSocket.send(jsonCommand);
        } else {
            Log.e(TAG, "WebSocket is not connected");
        }
    }
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    private class EchoWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
           // appendOutput("Connected to WebSocket");
            Log.d(TAG, "Connected to WebSocket");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.d(TAG, "Receiving message: " + text);   //For testing purposes
            Message = text;

            if (messageListener != null) {
                messageListener.onMessage(text);
            }
            }




        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
           // appendOutput("WebSocket closing: " + reason);
            Log.d(TAG, "WebSocket closing: " + reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
           // appendOutput("WebSocket error: " + t.getMessage());
            Log.d(TAG, "WebSocket error: " + t.getMessage());
        }

//        private void appendOutput(String message) {
//            output.post(() -> output.append("\n" + message));
//        }
    }
}
