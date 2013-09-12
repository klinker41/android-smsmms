/*
 * Copyright 2013 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.klinker.android.send_message;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.mms.transaction.HttpUtils;
import com.android.mms.transaction.ProgressCallbackEntity;
import com.google.android.mms.APN;
import com.google.android.mms.APNHelper;
import com.google.android.mms.MMSPart;
import com.google.android.mms.pdu_alt.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to process transaction requests for sending
 * @author Jake Klinker
 */
public class Transaction {

    public Settings settings;
    public Context context;
    public ConnectivityManager mConnMgr;

    public static final String SMS_SENT = "com.klinker.android.send_message.SMS_SENT";
    public static final String SMS_DELIVERED = "com.klinker.android.send_message.SMS_DELIVERED";
    public static final String MMS_ERROR = "com.klinker.android.send_message.MMS_ERROR";
    public static final String REFRESH = "com.klinker.android.send_message.REFRESH";
    public static final String MMS_PROGRESS = "com.klinker.android.send_message.MMS_PROGRESS";
    public static final String VOICE_FAILED = "com.klinker.android.send_message.VOICE_FAILED";
    public static final String VOICE_TOKEN = "com.klinker.android.send_message.RNRSE";
    public static final String NOTIFY_OF_DELIVERY = "com.klinker.android.send_message.NOTIFY_DELIVERY";
    public static final String NOTIFY_SMS_FAILURE = "com.klinker.android.send_message.NOTIFY_SMS_FAILURE";

    /**
     * Sets context and initializes settings to default values
     * @param context is the context of the activity or service
     */
    public Transaction(Context context) {
        settings = new Settings();
        this.context = context;
    }

    /**
     * Sets context and settings
     * @param context is the context of the activity or service
     * @param settings is the settings object to process send requests through
     */
    public Transaction(Context context, Settings settings) {
        this.settings = settings;
        this.context = context;
    }

    /**
     * Called to send a new message depending on settings and provided Message object
     * @param message is the message that you want to send
     * @param threadId is the thread id of who to send the message to (can be nullified)
     */
    public void sendNewMessage(final Message message, final String threadId) {

        // if message:
        //      1) Has images attached
        // or
        //      1) is enabled to send long messages as mms
        //      2) number of pages for that sms exceeds value stored in settings for when to send the mms by
        //      3) prefer voice is disabled
        // or
        //      1) more than one address is attached
        //      2) group messaging is enabled
        //
        // then, send as MMS, else send as Voice or SMS
        if (checkMMS(message)) {
            Log.v("sending_mms_library", "starting sending mms");
            sendMmsMessage(message.getText(), message.getAddresses(), message.getImages(), threadId);
        } else {
            if (settings.getPreferVoice()) {
                sendVoiceMessage(message.getText(), message.getAddresses(), threadId);
            } else {
                sendSmsMessage(message.getText(), message.getAddresses(), threadId);
            }
        }

    }

    private void sendSmsMessage(String text, String[] addresses, String threadId) {
        // set up sent and delivered pending intents to be used with message request
        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, new Intent(SMS_SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, new Intent(SMS_DELIVERED), 0);

        ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>();
        ArrayList<PendingIntent> dPI = new ArrayList<PendingIntent>();

        String body = text;

        // edit the body of the text if unicode needs to be stripped or signature needs to be added
        if (settings.getStripUnicode()) {
            body = StripAccents.stripAccents(body);
        }

        if (!settings.getSignature().equals("")) {
            body += "\n" + settings.getSignature();
        }

        SmsManager smsManager = SmsManager.getDefault();

        if (settings.getSplit()) {
            // figure out the length of supported message
            int length = 160;

            String patternStr = "[^" + Utils.GSM_CHARACTERS_REGEX + "]";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {
                length = 70;
            }

            boolean counter = false;

            if (settings.getSplitCounter()) {
                counter = true;
                length -= 7;
            }

            // get the split messages
            String[] textToSend = splitByLength(body, length, counter);

            // send each message part to each recipient attached to message
            for (int i = 0; i < textToSend.length; i++) {
                ArrayList<String> parts = smsManager.divideMessage(textToSend[i]);

                for (int j = 0; j < parts.size(); j++) {
                    sPI.add(sentPI);
                    dPI.add(settings.getDeliveryReports() ? deliveredPI : null);
                }

                for (int j = 0; j < addresses.length; j++) {
                    smsManager.sendMultipartTextMessage(addresses[j], null, parts, sPI, dPI);
                }
            }
        } else
        {
            // send the message normally without forcing anything to be split
            ArrayList<String> parts = smsManager.divideMessage(body);

            for (int i = 0; i < parts.size(); i++) {
                sPI.add(sentPI);
                dPI.add(settings.getDeliveryReports() ? deliveredPI : null);
            }

            try {
                for (int i = 0; i < addresses.length; i++) {
                    smsManager.sendMultipartTextMessage(addresses[i], null, parts, sPI, dPI);
                }
            } catch (Exception e) {
                // whoops...
                e.printStackTrace();

                try {
                    ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(context, "Error, check the \"Split SMS\" option in advanced settings and retry.", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception f) {

                }
            }
        }

        // add signature to original text to be saved in database (does not strip unicode for saving though)
        if (!settings.getSignature().equals("")) {
            text += "\n" + settings.getSignature();
        }

        // save the message for each of the addresses
        for (int i = 0; i < addresses.length; i++) {
            Calendar cal = Calendar.getInstance();
            ContentValues values = new ContentValues();
            values.put("address", addresses[i]);
            values.put("body", settings.getStripUnicode() ? StripAccents.stripAccents(text) : text);
            values.put("date", cal.getTimeInMillis() + "");
            values.put("read", 1);

            // attempt to create correct thread id if one is not supplied
            if (threadId == null || addresses.length > 1) {
                threadId = Telephony.Threads.getOrCreateThreadId(context, addresses[i]) + "";
            }

            values.put("thread_id", threadId);
            context.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
        }
    }

    private void sendMmsMessage(String text, String[] addresses, Bitmap[] image, String threadId) {
        // merge the string[] of addresses into a single string so they can be inserted into the database easier
        String address = "";

        for (int i = 0; i < addresses.length; i++) {
            address += addresses[i] + " ";
        }

        address = address.trim();

        // create the parts to send
        ArrayList<MMSPart> data = new ArrayList<MMSPart>();

        for (int i = 0; i < image.length; i++) {
            // turn bitmap into byte array to be stored
            byte[] imageBytes = Message.bitmapToByteArray(image[i]);

            MMSPart part = new MMSPart();
            part.MimeType = "image/png";
            part.Name = "Image";
            part.Data = imageBytes;
            data.add(part);
        }

        // add text to the end of the part and send
        MMSPart part = new MMSPart();
        part.Name = "Text";
        part.MimeType = "text/plain";
        part.Data = text.getBytes();
        data.add(part);

        // insert the pdu into the database and return the bytes to send
        if (settings.getWifiMmsFix()) {
            sendMMS(getBytes(address.split(" "), data.toArray(new MMSPart[data.size()])));
        } else {
            sendMMSWiFi(getBytes(address.split(" "), data.toArray(new MMSPart[data.size()])));
        }
    }

    private byte[] getBytes(String[] recipients, MMSPart[] parts) {
        final SendReq sendRequest = new SendReq();

        // create send request addresses
        for (int i = 0; i < recipients.length; i++) {
            final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);

            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }

        sendRequest.setDate(Calendar.getInstance().getTimeInMillis() / 1000L);

        try {
            sendRequest.setFrom(new EncodedStringValue(Utils.getMyPhoneNumber(context)));
        } catch (Exception e) {
            // my number is nothing
        }

        final PduBody pduBody = new PduBody();

        // assign parts to the pdu body which contains sending data
        if (parts != null) {
            for (MMSPart part : parts) {
                if (part != null) {
                    try {
                        final PduPart partPdu = new PduPart();
                        partPdu.setName(part.Name.getBytes());
                        partPdu.setContentType(part.MimeType.getBytes());
                        partPdu.setData(part.Data);
                        pduBody.addPart(partPdu);
                    } catch (Exception e) {

                    }
                }
            }
        }

        sendRequest.setBody(pduBody);

        // create byte array which will actually be sent
        final PduComposer composer = new PduComposer(context, sendRequest);
        final byte[] bytesToSend = composer.make();

        try {
            PduPersister persister = PduPersister.getPduPersister(context);
            persister.persist(sendRequest, Telephony.Mms.Outbox.CONTENT_URI, true, settings.getGroup(), null);
        } catch (Exception e) {
            Log.v("sending_mms_library", "error saving mms message");
            e.printStackTrace();

            // use the old way if something goes wrong with the persister
            insert(recipients, parts);
        }

        return bytesToSend;
    }

    private void sendVoiceMessage(String text, String[] addresses, String threadId) {
        // send a voice message to each recipient based off of koush's voice implementation in Voice+
        for (int i = 0; i < addresses.length; i++) {
            Calendar cal = Calendar.getInstance();
            ContentValues values = new ContentValues();
            values.put("address", addresses[i]);
            values.put("body", text);
            values.put("date", cal.getTimeInMillis() + "");
            values.put("read", 1);
            values.put("status", 2);   // if you want to be able to tell the difference between sms and voice, look for this value. SMS will be -1, 0, 64, 128 and voice will be 2

            // attempt to create correct thread id if one is not supplied
            if (threadId == null || addresses.length > 1) {
                threadId = Telephony.Threads.getOrCreateThreadId(context, addresses[i]) + "";
            }

            values.put("thread_id", threadId);
            context.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);

            if (!settings.getSignature().equals("")) {
                text += "\n" + settings.getSignature();
            }

            sendVoiceMessage(addresses[i], text);
        }
    }

    // splits text and adds split counter when applicable
    private String[] splitByLength(String s, int chunkSize, boolean counter) {
        int arraySize = (int) Math.ceil((double) s.length() / chunkSize);

        String[] returnArray = new String[arraySize];

        int index = 0;
        for(int i = 0; i < s.length(); i = i+chunkSize) {
            if(s.length() - i < chunkSize) {
                returnArray[index++] = s.substring(i);
            } else {
                returnArray[index++] = s.substring(i, i+chunkSize);
            }
        }

        if (counter && returnArray.length > 1) {
            for (int i = 0; i < returnArray.length; i++) {
                returnArray[i] = "(" + (i+1) + "/" + returnArray.length + ") " + returnArray[i];
            }
        }

        return returnArray;
    }

    private boolean alreadySending = false;

    private void sendMMS(final byte[] bytesToSend) {
        revokeWifi(true);

        // enable mms connection to mobile data
        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        int result = beginMmsConnectivity();

        Log.v("sending_mms_library", "result of connectivity: " + result + " ");

        if (result != 0) {
            // if mms feature is not already running (most likely isn't...) then register a receiver and wait for it to be active
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            final BroadcastReceiver receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context1, Intent intent) {
                    String action = intent.getAction();

                    if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                        return;
                    }

                    @SuppressWarnings("deprecation")
                    NetworkInfo mNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                    if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                        return;
                    }

                    if (!mNetworkInfo.isConnected()) {
                        return;
                    } else {
                        // ready to send the message now
                        Log.v("sending_mms_library", "sending through broadcast receiver");
                        alreadySending = true;
                        sendData(bytesToSend);

                        context.unregisterReceiver(this);
                    }

                }

            };

            context.registerReceiver(receiver, filter);

            // try sending after 3 seconds anyways if for some reason the receiver doesn't work
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!alreadySending) {
                        try {
                            Log.v("sending_mms_library", "sending through handler");
                            context.unregisterReceiver(receiver);
                        } catch (Exception e) {

                        }

                        sendData(bytesToSend);
                    }
                }
            }, 7000);
        } else {
            // mms connection already active, so send the message
            Log.v("sending_mms_library", "sending right away, already ready");
            sendData(bytesToSend);
        }
    }

    private void sendMMSWiFi(final byte[] bytesToSend) {
        // enable mms connection to mobile data
        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo.State state = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS).getState();

        if ((0 == state.compareTo(NetworkInfo.State.CONNECTED) || 0 == state.compareTo(NetworkInfo.State.CONNECTING))) {
            sendData(bytesToSend);
        } else {
            int resultInt = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

            if (resultInt == 0) {
                sendData(bytesToSend);
            } else {
                // if mms feature is not already running (most likely isn't...) then register a receiver and wait for it to be active
                IntentFilter filter = new IntentFilter();
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                final BroadcastReceiver receiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context1, Intent intent) {
                        String action = intent.getAction();

                        if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                            return;
                        }

                        NetworkInfo mNetworkInfo = mConnMgr.getActiveNetworkInfo();
                        if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE_MMS)) {
                            return;
                        }

                        if (!mNetworkInfo.isConnected()) {
                            return;
                        } else {
                            alreadySending = true;
                            Utils.forceMobileConnectionForAddress(mConnMgr, settings.getMmsc());
                            sendData(bytesToSend);

                            context.unregisterReceiver(this);
                        }

                    }

                };

                context.registerReceiver(receiver, filter);

                // try sending after 3 seconds anyways if for some reason the receiver doesn't work
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!alreadySending) {
                            try {
                                context.unregisterReceiver(receiver);
                            } catch (Exception e) {

                            }

                            sendData(bytesToSend);
                        }
                    }
                }, 7000);
            }
        }
    }

    private void sendData(final byte[] bytesToSend) {
        // be sure this is running on new thread, not UI
        Log.v("sending_mms_library", "starting new thread to send on");
        new Thread(new Runnable() {

            @Override
            public void run() {
                List<APN> apns = new ArrayList<APN>();

                APN apn = new APN(settings.getMmsc(), settings.getPort(), settings.getProxy());
                apns.add(apn);

                String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                apns.get(0).MMSCenterUrl = mmscUrl;

                if (apns.get(0).MMSCenterUrl.equals("")) {
                    // attempt to get apns from internal databases, most likely will fail due to insignificant permissions
                    APNHelper helper = new APNHelper(context);
                    apns = helper.getMMSApns();
                }

                Log.v("sending_mms_library", apns.get(0).MMSCenterUrl + " " + apns.get(0).MMSProxy + " " + apns.get(0).MMSPort);

                try {
                    // attempts to send the message using given apns
                    Log.v("sending_mms_libarry", "initial attempt at sending starting now");
                    trySending(apns.get(0), bytesToSend, 0);
                } catch (Exception e) {
                    // some type of apn error, so notify user of failure
                    Log.v("sending_mms_libary", "weird error, not sure how this could even be called other than apn stuff");
                    markMmsFailed();
                }

            }

        }).start();
    }

    public static final int NUM_RETRIES = 2;

    private void trySending(final APN apns, final byte[] bytesToSend, final int numRetries) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ProgressCallbackEntity.PROGRESS_STATUS_ACTION);
            BroadcastReceiver receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    int progress = intent.getIntExtra("progress", -3);
                    Log.v("sending_mms_library", "progress: " + progress);

                    // send progress broadcast to update ui if desired...
                    Intent progressIntent = new Intent(MMS_PROGRESS);
                    progressIntent.putExtra("progress", progress);
                    context.sendBroadcast(progressIntent);

                    if (progress == ProgressCallbackEntity.PROGRESS_COMPLETE) {
                        Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[] {"_id"}, null, null, "date desc");
                        query.moveToFirst();
                        String id = query.getString(query.getColumnIndex("_id"));
                        query.close();

                        // move to the sent box
                        ContentValues values = new ContentValues();
                        values.put("msg_box", 2);
                        String where = "_id" + " = '" + id + "'";
                        context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);

                        context.sendBroadcast(new Intent(REFRESH));
                        context.unregisterReceiver(this);

                        // give everything time to finish up, may help the abort being shown after the progress is already 100
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (settings.getWifiMmsFix()) {
                                    reinstateWifi();
                                }
                            }
                        }, 5000);
                    } else if (progress == ProgressCallbackEntity.PROGRESS_ABORT) {
                        // This seems to get called only after the progress has reached 100 and then something else goes wrong, so here we will try and send again and see if it works
                        Log.v("sending_mms_library", "sending aborted for some reason...");
                        context.unregisterReceiver(this);

                        if (numRetries < NUM_RETRIES) {
                            // sleep and try again in three seconds to see if that give wifi and mobile data a chance to toggle in time
                            try {
                                Thread.sleep(3000);
                            } catch (Exception f) {

                            }

                            if (settings.getWifiMmsFix()) {
                                sendMMS(bytesToSend);
                            } else {
                                sendMMSWiFi(bytesToSend);
                            }
                        } else {
                            markMmsFailed();
                        }
                    }
                }

            };

            context.registerReceiver(receiver, filter);

            // This is where the actual post request is made to send the bytes we previously created through the given apns
            Log.v("sending_mms_library", "attempt: " + numRetries);
            Utils.ensureRouteToHost(context, apns.MMSCenterUrl, apns.MMSProxy);
            HttpUtils.httpConnection(context, 4444L, apns.MMSCenterUrl, bytesToSend, HttpUtils.HTTP_POST_METHOD, !TextUtils.isEmpty(apns.MMSProxy), apns.MMSProxy, Integer.parseInt(apns.MMSPort));
        } catch (IOException e) {
            Log.v("sending_mms_library", "some type of error happened when actually sending maybe?");
            e.printStackTrace();

            if (numRetries < NUM_RETRIES) {
                // sleep and try again in three seconds to see if that give wifi and mobile data a chance to toggle in time
                try {
                    Thread.sleep(3000);
                } catch (Exception f) {

                }

                trySending(apns, bytesToSend, numRetries + 1);
            } else {
                markMmsFailed();
            }
        }
    }

    private void markMmsFailed() {
        // if it still fails, then mark message as failed
        if (settings.getWifiMmsFix()) {
            reinstateWifi();
        }

        Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[] {"_id"}, null, null, "date desc");
        query.moveToFirst();
        String id = query.getString(query.getColumnIndex("_id"));
        query.close();

        // mark message as failed
        ContentValues values = new ContentValues();
        values.put("msg_box", 5);
        String where = "_id" + " = '" + id + "'";
        context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);

        ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

            @Override
            public void run() {
                context.sendBroadcast(new Intent(REFRESH));

                // broadcast that mms has failed and you can notify user from there if you would like
                context.sendBroadcast(new Intent(MMS_ERROR));

            }

        });
    }

    private void sendVoiceMessage(String destAddr, String text) {
        String rnrse = settings.getRnrSe();
        String account = settings.getAccount();
        String authToken;

        try {
            authToken = Utils.getAuthToken(account, context);

            if (rnrse == null) {
                rnrse = fetchRnrSe(authToken, context);
            }
        } catch (Exception e) {
            failVoice();
            return;
        }

        try {
            sendRnrSe(authToken, rnrse, destAddr, text);
            successVoice();
            return;
        } catch (Exception e) {

        }

        try {
            // try again...
            rnrse = fetchRnrSe(authToken, context);
            sendRnrSe(authToken, rnrse, destAddr, text);
            successVoice();
        } catch (Exception e) {
            failVoice();
        }
    }

    // hit the google voice api to send a text
    private void sendRnrSe(String authToken, String rnrse, String number, String text) throws Exception {
        JsonObject json = Ion.with(context)
                .load("https://www.google.com/voice/sms/send/")
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .setBodyParameter("phoneNumber", number)
                .setBodyParameter("sendErrorSms", "0")
                .setBodyParameter("text", text)
                .setBodyParameter("_rnr_se", rnrse)
                .asJsonObject()
                .get();

        if (!json.get("ok").getAsBoolean())
            throw new Exception(json.toString());
    }

    private void failVoice() {
        Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

        // mark message as failed
        if (query.moveToFirst()) {
            String id = query.getString(query.getColumnIndex("_id"));
            ContentValues values = new ContentValues();
            values.put("type", "5");
            values.put("read", true);
            context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
        }

        query.close();

        context.sendBroadcast(new Intent(REFRESH));
        context.sendBroadcast(new Intent(VOICE_FAILED));
    }

    private void successVoice() {
        Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

        // mark message as sent successfully
        if (query.moveToFirst()){
            String id = query.getString(query.getColumnIndex("_id"));
            ContentValues values = new ContentValues();
            values.put("type", "2");
            values.put("read", true);
            context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
        }

        query.close();

        context.sendBroadcast(new Intent(REFRESH));
    }

    private String fetchRnrSe(String authToken, Context context) throws ExecutionException, InterruptedException {
        JsonObject userInfo = Ion.with(context)
                .load("https://www.google.com/voice/request/user")
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .asJsonObject()
                .get();

        String rnrse = userInfo.get("r").getAsString();

        try {
            TelephonyManager tm = (TelephonyManager)context.getSystemService(Activity.TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry: phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString()))
                        continue;
                    if (!phone.get("smsEnabled").getAsBoolean())
                        break;

                    Ion.with(context)
                            .load("https://www.google.com/voice/settings/editForwardingSms/")
                            .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                            .setBodyParameter("phoneId", entry.getKey())
                            .setBodyParameter("enabled", "0")
                            .setBodyParameter("_rnr_se", rnrse)
                            .asJsonObject();
                    break;
                }
            }
        } catch (Exception e) {

        }

        // broadcast so you can save it to your shared prefs or something so that it doesn't need to be retrieved every time
        Intent intent = new Intent(VOICE_TOKEN);
        intent.putExtra("_rnr_se", rnrse);
        context.sendBroadcast(intent);

        return rnrse;
    }

    private Uri insert(String[] to, MMSPart[] parts) {
        try {
            Uri destUri = Uri.parse("content://mms");

            Set<String> recipients = new HashSet<String>();
            recipients.addAll(Arrays.asList(to));
            long thread_id = Telephony.Threads.getOrCreateThreadId(context, recipients);

            // Create a dummy sms
            ContentValues dummyValues = new ContentValues();
            dummyValues.put("thread_id", thread_id);
            dummyValues.put("body", " ");
            Uri dummySms = context.getContentResolver().insert(Uri.parse("content://sms/sent"), dummyValues);

            // Create a new message entry
            long now = System.currentTimeMillis();
            ContentValues mmsValues = new ContentValues();
            mmsValues.put("thread_id", thread_id);
            mmsValues.put("date", now/1000L);
            mmsValues.put("msg_box", 4);
            //mmsValues.put("m_id", System.currentTimeMillis());
            mmsValues.put("read", true);
            mmsValues.put("sub", "");
            mmsValues.put("sub_cs", 106);
            mmsValues.put("ct_t", "application/vnd.wap.multipart.related");

            long imageBytes = 0;

            for (MMSPart part : parts) {
                imageBytes += part.Data.length;
            }

            mmsValues.put("exp", imageBytes);

            mmsValues.put("m_cls", "personal");
            mmsValues.put("m_type", 128); // 132 (RETRIEVE CONF) 130 (NOTIF IND) 128 (SEND REQ)
            mmsValues.put("v", 19);
            mmsValues.put("pri", 129);
            mmsValues.put("tr_id", "T"+ Long.toHexString(now));
            mmsValues.put("resp_st", 128);

            // Insert message
            Uri res = context.getContentResolver().insert(destUri, mmsValues);
            String messageId = res.getLastPathSegment().trim();

            // Create part
            for (MMSPart part : parts) {
                if (part.MimeType.startsWith("image")) {
                    createPartImage(messageId, part.Data, part.MimeType);
                } else if (part.MimeType.startsWith("text")) {
                    createPartText(messageId, new String(part.Data, "UTF-8"));
                }
            }

            // Create addresses
            for (String addr : to) {
                createAddr(messageId, addr);
            }

            //res = Uri.parse(destUri + "/" + messageId);

            // Delete dummy sms
            context.getContentResolver().delete(dummySms, null, null);

            return res;
        } catch (Exception e) {
            Log.v("sending_mms_library", "still an error saving... :(");
            e.printStackTrace();
        }

        return null;
    }

    // create the image part to be stored in database
    private Uri createPartImage(String id, byte[] imageBytes, String mimeType) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", mimeType);
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        // Add data to part
        OutputStream os = context.getContentResolver().openOutputStream(res);
        ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
        byte[] buffer = new byte[256];

        for (int len=0; (len=is.read(buffer)) != -1;) {
            os.write(buffer, 0, len);
        }

        os.close();
        is.close();

        return res;
    }

    // create the text part to be stored in database
    private Uri createPartText(String id, String text) throws Exception {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", "text/plain");
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        mmsPartValue.put("text", text);
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        return res;
    }

    // add address to the request
    private Uri createAddr(String id, String addr) throws Exception {
        ContentValues addrValues = new ContentValues();
        addrValues.put("address", addr);
        addrValues.put("charset", "106");
        addrValues.put("type", 151); // TO
        Uri addrUri = Uri.parse("content://mms/"+ id +"/addr");
        Uri res = context.getContentResolver().insert(addrUri, addrValues);

        return res;
    }

    /**
     * A method for checking whether or not a certain message will be sent as mms depending on its contents and the settings
     * @param message is the message that you are checking against
     * @return true if the message will be mms, otherwise false
     */
    public boolean checkMMS(Message message) {
        return message.getImages().length != 0 || (settings.getSendLongAsMms() && Utils.getNumPages(settings, message.getText()) > settings.getSendLongAsMmsAfter() && !settings.getPreferVoice()) || (message.getAddresses().length > 1 && settings.getGroup());
    }

    // FIXME again with the wifi problems... should not have to do this at all
    /**
     * @deprecated
     */
    private void reinstateWifi() {
        try {
            context.unregisterReceiver(settings.discon);
        } catch (Exception f) {

        }

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        wifi.setWifiEnabled(settings.currentWifiState);
        wifi.reconnect();
        Utils.setMobileDataEnabled(context, settings.currentDataState);
    }

    // FIXME it should not be required to disable wifi and enable mobile data manually, but I have found no way to use the two at the same time
    /**
     * @deprecated
     */
    private void revokeWifi(boolean saveState) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (saveState) {
            settings.currentWifi = wifi.getConnectionInfo();
            settings.currentWifiState = wifi.isWifiEnabled();
            wifi.disconnect();
            settings.discon = new DisconnectWifi();
            context.registerReceiver(settings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            settings.currentDataState = Utils.isMobileDataEnabled(context);
            Utils.setMobileDataEnabled(context, true);
        } else {
            wifi.disconnect();
            wifi.disconnect();
            settings.discon = new DisconnectWifi();
            context.registerReceiver(settings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            Utils.setMobileDataEnabled(context, true);
        }
    }

    /**
     * @deprecated
     */
    private int beginMmsConnectivity() {
        Log.v("sending_mms_library", "starting mms service");
        return mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");
    }
}
