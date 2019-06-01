package com.klinker.android.send_message;

import android.content.Context;

public abstract class OwnNumber {
    public String yield(Context context) {
        return onYield(context);
    }
    public abstract String onYield(Context context);
}
