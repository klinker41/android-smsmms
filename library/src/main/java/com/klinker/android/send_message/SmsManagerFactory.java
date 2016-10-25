package com.klinker.android.send_message;

import android.os.Build;
import android.telephony.SmsManager;

public class SmsManagerFactory {

    public static SmsManager createSmsManager(Settings settings) {
        return createSmsManager(settings.getSubscriptionId());
    }

    public static SmsManager createSmsManager(int subscriptionId) {
        if (subscriptionId != Settings.DEFAULT_SUBSCRIPTION_ID &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        } else {
            return SmsManager.getDefault();
        }
    }
}
