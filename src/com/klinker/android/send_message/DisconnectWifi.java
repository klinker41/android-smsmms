package com.klinker.android.send_message;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;

public class DisconnectWifi extends BroadcastReceiver  {

    @Override
    public void onReceive(Context c, Intent intent) {
    	WifiManager wifi = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
        if(!intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE).toString().equals(SupplicantState.SCANNING)) 
        	wifi.disconnect();
    }
}