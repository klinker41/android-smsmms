package com.klinker.android.messaging_sample;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.klinker.android.send_message.MmsReceivedReceiver;

public class MmsReceivedReceiverImpl extends MmsReceivedReceiver {

    @Override
    public void onMessageReceived(Context context, Uri messageUri) {
        Log.v("MmsReceived", "message received: " + messageUri.toString());
    }

    @Override
    public void onError(Context context, String error) {
        Log.v("MmsReceived", "error: " + error);
    }

}
