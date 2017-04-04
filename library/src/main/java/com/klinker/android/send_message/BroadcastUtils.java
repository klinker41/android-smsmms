package com.klinker.android.send_message;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class BroadcastUtils {

    public static void sendExplicitBroadcast(Context context, Intent intent, String action) {
        PackageManager pm = context.getPackageManager();

        try {
            PackageInfo packageInfo =
                    pm.getPackageInfo(context.getPackageName(), PackageManager.GET_RECEIVERS);

            ActivityInfo[] receivers = packageInfo.receivers;
            for (ActivityInfo receiver : receivers) {
                if (receiver.taskAffinity.equals(action)) {
                    intent.setClassName(receiver.packageName, receiver.name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        intent.setPackage(context.getPackageName());
        intent.setAction(action);
        context.sendBroadcast(intent);
    }
}
