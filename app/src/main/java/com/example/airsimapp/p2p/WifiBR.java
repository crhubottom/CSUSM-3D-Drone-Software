package com.example.airsimapp.p2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;

public class WifiBR extends BroadcastReceiver {

    private final WifiP2pController controller;

    public WifiBR(WifiP2pController controller) {
        this.controller = controller;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                controller.notifyStatus("Wi-Fi Direct is ON");
            } else {
                controller.notifyStatus("Wi-Fi Direct is OFF");
            }

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            controller.handlePeersChanged();

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            controller.handleConnectionChanged();

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            controller.notifyStatus("This device's Wi-Fi Direct info changed");
        }
    }
}