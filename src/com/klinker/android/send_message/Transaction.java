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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.android.mms.transaction.HttpUtils;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.transaction.ProgressCallbackEntity;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.RateController;
import com.google.android.mms.*;
import com.google.android.mms.pdu_alt.*;
import com.google.android.mms.smil.SmilHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Class to process transaction requests for sending
 *
 * @author Jake Klinker
 */
public class Transaction {

    private Context context;
    private ConnectivityManager mConnMgr;

    private boolean saveMessage = true;

    public String SMS_SENT = ".SMS_SENT";
    public String SMS_DELIVERED = ".SMS_DELIVERED";

    public static String NOTIFY_SMS_FAILURE = ".NOTIFY_SMS_FAILURE";
    public static final String MMS_ERROR = "com.klinker.android.send_message.MMS_ERROR";
    public static final String REFRESH = "com.klinker.android.send_message.REFRESH";
    public static final String MMS_PROGRESS = "com.klinker.android.send_message.MMS_PROGRESS";
    public static final String VOICE_FAILED = "com.klinker.android.send_message.VOICE_FAILED";
    public static final String VOICE_TOKEN = "com.klinker.android.send_message.RNRSE";
    public static final String NOTIFY_OF_DELIVERY = "com.klinker.android.send_message.NOTIFY_DELIVERY";
    public static final String NOTIFY_OF_MMS = "com.klinker.android.messaging.NEW_MMS_DOWNLOADED";

    public static final long NO_THREAD_ID = 0;

    /**
     * Sets context and settings
     *
     * @param context is the context of the activity or service
     */
    public Transaction(Context context) {
        this.context = context;
        SMS_SENT = context.getPackageName() + SMS_SENT;
        SMS_DELIVERED = context.getPackageName() + SMS_DELIVERED;

        if (NOTIFY_SMS_FAILURE.equals(".NOTIFY_SMS_FAILURE")) {
            NOTIFY_SMS_FAILURE = context.getPackageName() + NOTIFY_SMS_FAILURE;
        }
    }

    /**
     * Called to send a new message depending on settings and provided Message object
     * If you want to send message as mms, call this from the UI thread
     *
     * @param message  is the message that you want to send
     * @param threadId is the thread id of who to send the message to (can also be set to Transaction.NO_THREAD_ID)
     */
    public void sendNewMessage(Message message, long threadId) {
        this.saveMessage = message.getSave();

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
            try {
                Looper.prepare();
            } catch (Exception e) {
            }
            RateController.init(context);
            DownloadManager.init(context);
            sendMmsMessage(message.getText(), message.getAddresses(), message.getImages(), message.getMedia(), message.getMediaMimeType(), message.getSubject());
        } else {
            if (message.getType() == Message.TYPE_VOICE) {
                sendVoiceMessage(message.getText(), message.getAddresses(), threadId);
            } else if (message.getType() == Message.TYPE_SMSMMS) {
                sendSmsMessage(message.getText(), message.getAddresses(), threadId);
            } else {
                Log.v("send_transaction", "error with message type, aborting...");
            }
        }

    }

    private void sendSmsMessage(String text, String[] addresses, long threadId) {
        Uri messageUri = null;
        int messageId = 0;
        if (saveMessage) {
            // add signature to original text to be saved in database (does not strip unicode for saving though)
            if (Settings.get().getSignature().length() > 0) {
                text += "\n" + Settings.get().getSignature();
            }

            // save the message for each of the addresses
            for (int i = 0; i < addresses.length; i++) {
                Calendar cal = Calendar.getInstance();
                ContentValues values = new ContentValues();
                values.put("address", addresses[i]);
                values.put("body", Settings.get().getStripUnicode() ? StripAccents.stripAccents(text) : text);
                values.put("date", cal.getTimeInMillis() + "");
                values.put("read", 1);
                values.put("type", 4);

                // attempt to create correct thread id if one is not supplied
                if (threadId == NO_THREAD_ID || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(context, addresses[i]);
                }

                values.put("thread_id", threadId);
                messageUri = context.getContentResolver().insert(Uri.parse("content://sms/"), values);

                Cursor query = context.getContentResolver().query(messageUri, new String[]{"_id"}, null, null, null);
                if (query != null && query.moveToFirst()) {
                    messageId = query.getInt(0);
                }

                // set up sent and delivered pending intents to be used with message request
                PendingIntent sentPI = PendingIntent.getBroadcast(context, messageId, new Intent(SMS_SENT)
                        .putExtra("message_uri", messageUri == null ? "" : messageUri.toString()), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context, messageId, new Intent(SMS_DELIVERED)
                        .putExtra("message_uri", messageUri == null ? "" : messageUri.toString()), PendingIntent.FLAG_UPDATE_CURRENT);

                ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>();
                ArrayList<PendingIntent> dPI = new ArrayList<PendingIntent>();

                String body = text;

                // edit the body of the text if unicode needs to be stripped
                if (Settings.get().getStripUnicode()) {
                    body = StripAccents.stripAccents(body);
                }

                if (!Settings.get().getPreText().equals("")) {
                    body = Settings.get().getPreText() + " " + body;
                }

                SmsManager smsManager = SmsManager.getDefault();

                if (Settings.get().getSplit()) {
                    // figure out the length of supported message
                    int[] splitData = SmsMessage.calculateLength(body, false);

                    // we take the current length + the remaining length to get the total number of characters
                    // that message set can support, and then divide by the number of message that will require
                    // to get the length supported by a single message
                    int length = (body.length() + splitData[2]) / splitData[0];

                    boolean counter = false;
                    if (Settings.get().getSplitCounter() && body.length() > length) {
                        counter = true;
                        length -= 6;
                    }

                    // get the split messages
                    String[] textToSend = splitByLength(body, length, counter);

                    // send each message part to each recipient attached to message
                    for (int j = 0; j < textToSend.length; j++) {
                        ArrayList<String> parts = smsManager.divideMessage(textToSend[j]);

                        for (int k = 0; k < parts.size(); k++) {
                            sPI.add(saveMessage ? sentPI : null);
                            dPI.add(Settings.get().getDeliveryReports() && saveMessage ? deliveredPI : null);
                        }

                        smsManager.sendMultipartTextMessage(addresses[i], null, parts, sPI, dPI);
                    }
                } else {
                    // send the message normally without forcing anything to be split
                    ArrayList<String> parts = smsManager.divideMessage(body);

                    for (int j = 0; j < parts.size(); j++) {
                        sPI.add(saveMessage ? sentPI : null);
                        dPI.add(Settings.get().getDeliveryReports() && saveMessage ? deliveredPI : null);
                    }

                    try {
                        smsManager.sendMultipartTextMessage(addresses[i], null, parts, sPI, dPI);
                    } catch (Exception e) {
                        // whoops...
                        e.printStackTrace();

                        try {
                            ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                @Override
                                public void run() {
                                    Toast.makeText(context, "Message could not be sent", Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (Exception f) {
                        }
                    }
                }
            }
        }
    }

    private void sendMmsMessage(String text, String[] addresses, Bitmap[] image, byte[] media, String mimeType, String subject) {
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
            part.MimeType = "image/jpeg";
            part.Name = "image" + i;
            part.Data = imageBytes;
            data.add(part);
        }

        // add any extra media according to their mimeType set in the message
        //      eg. videos, audio, contact cards, location maybe?
        if (media.length > 0 && mimeType != null) {
            MMSPart part = new MMSPart();
            part.MimeType = mimeType;
            part.Name = mimeType.split("/")[0];
            part.Data = media;
            data.add(part);
        }

        if (!text.equals("")) {
            // add text to the end of the part and send
            MMSPart part = new MMSPart();
            part.Name = "text";
            part.MimeType = "text/plain";
            part.Data = text.getBytes();
            data.add(part);
        }

        MessageInfo info;

        try {
            info = getBytes(address.split(" "), data.toArray(new MMSPart[data.size()]), subject);
        } catch (MmsException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MmsMessageSender sender = new MmsMessageSender(context, info.location, info.bytes.length);
            sender.sendMessage(info.token);

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
                        context.sendBroadcast(new Intent(REFRESH));
                        context.unregisterReceiver(this);
                    } else if (progress == ProgressCallbackEntity.PROGRESS_ABORT) {
                        // This seems to get called only after the progress has reached 100 and then something else goes wrong, so here we will try and send again and see if it works
                        Log.v("sending_mms_library", "sending aborted for some reason...");
                    }
                }

            };

            context.registerReceiver(receiver, filter);
        } catch (Throwable e) {
            e.printStackTrace();
            // insert the pdu into the database and return the bytes to send
            if (Settings.get().getWifiMmsFix()) {
                sendMMS(info.bytes);
            } else {
                sendMMSWiFi(info.bytes);
            }
        }
    }

    private MessageInfo getBytes(String[] recipients, MMSPart[] parts, String subject)
            throws MmsException {
        final SendReq sendRequest = new SendReq();

        // create send request addresses
        for (int i = 0; i < recipients.length; i++) {
            final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);

            if (phoneNumbers != null && phoneNumbers.length > 0) {
                sendRequest.addTo(phoneNumbers[0]);
            }
        }

        if (subject != null) {
            sendRequest.setSubject(new EncodedStringValue(subject));
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
            for (int i = 0; i < parts.length; i++) {
                MMSPart part = parts[i];
                if (part != null) {
                    try {
                        PduPart partPdu = new PduPart();
                        partPdu.setName(part.Name.getBytes());
                        partPdu.setContentType(part.MimeType.getBytes());

                        if (part.MimeType.startsWith("text")) {
                            partPdu.setCharset(CharacterSets.UTF_8);
                        }

                        partPdu.setData(part.Data);

                        pduBody.addPart(partPdu);
                    } catch (Exception e) {

                    }
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(pduBody), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        pduBody.addPart(0, smilPart);

        sendRequest.setBody(pduBody);

        // create byte array which will actually be sent
        final PduComposer composer = new PduComposer(context, sendRequest);
        final byte[] bytesToSend;

        try {
            bytesToSend = composer.make();
        } catch (OutOfMemoryError e) {
            throw new MmsException("Out of memory!");
        }

        MessageInfo info = new MessageInfo();
        info.bytes = bytesToSend;

        if (saveMessage) {
            try {
                PduPersister persister = PduPersister.getPduPersister(context);
                info.location = persister.persist(sendRequest, Uri.parse("content://mms/outbox"), true, Settings.get().getGroup(), null);
            } catch (Exception e) {
                Log.v("sending_mms_library", "error saving mms message");
                e.printStackTrace();

                // use the old way if something goes wrong with the persister
                insert(recipients, parts, subject);
            }
        }

        Cursor query = context.getContentResolver().query(info.location, new String[]{"thread_id"}, null, null, null);
        if (query != null && query.moveToFirst()) {
            info.token = query.getLong(query.getColumnIndex("thread_id"));
        } else {
            // just default sending token for what I had before
            info.token = 4444L;
        }

        return info;
    }

    private class MessageInfo {
        public long token;
        public Uri location;
        public byte[] bytes;
    }

    private void sendVoiceMessage(String text, String[] addresses, long threadId) {
        // send a voice message to each recipient based off of koush's voice implementation in Voice+
        for (int i = 0; i < addresses.length; i++) {
            if (saveMessage) {
                Calendar cal = Calendar.getInstance();
                ContentValues values = new ContentValues();
                values.put("address", addresses[i]);
                values.put("body", text);
                values.put("date", cal.getTimeInMillis() + "");
                values.put("read", 1);
                values.put("status", 2);   // if you want to be able to tell the difference between sms and voice, look for this value. SMS will be -1, 0, 64, 128 and voice will be 2

                // attempt to create correct thread id if one is not supplied
                if (threadId == NO_THREAD_ID || addresses.length > 1) {
                    threadId = Utils.getOrCreateThreadId(context, addresses[i]);
                }

                values.put("thread_id", threadId);
                context.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
            }

            if (!Settings.get().getSignature().equals("")) {
                text += "\n" + Settings.get().getSignature();
            }

            sendVoiceMessage(addresses[i], text);
        }
    }

    // splits text and adds split counter when applicable
    private String[] splitByLength(String s, int chunkSize, boolean counter) {
        int arraySize = (int) Math.ceil((double) s.length() / chunkSize);

        String[] returnArray = new String[arraySize];

        int index = 0;
        for (int i = 0; i < s.length(); i = i + chunkSize) {
            if (s.length() - i < chunkSize) {
                returnArray[index++] = s.substring(i);
            } else {
                returnArray[index++] = s.substring(i, i + chunkSize);
            }
        }

        if (counter && returnArray.length > 1) {
            for (int i = 0; i < returnArray.length; i++) {
                returnArray[i] = "(" + (i + 1) + "/" + returnArray.length + ") " + returnArray[i];
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

            try {
                Looper.prepare();
            } catch (Exception e) {
                // Already on UI thread probably
            }

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
                try {
                    Utils.ensureRouteToHost(context, Settings.get().getMmsc(), Settings.get().getProxy());
                    sendData(bytesToSend);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendData(bytesToSend);
                }
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

                            try {
                                Utils.ensureRouteToHost(context, Settings.get().getMmsc(), Settings.get().getProxy());
                                sendData(bytesToSend);
                            } catch (Exception e) {
                                e.printStackTrace();
                                sendData(bytesToSend);
                            }

                            context.unregisterReceiver(this);
                        }

                    }

                };

                context.registerReceiver(receiver, filter);

                try {
                    Looper.prepare();
                } catch (Exception e) {
                    // Already on UI thread probably
                }

                // try sending after 3 seconds anyways if for some reason the receiver doesn't work
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!alreadySending) {
                            try {
                                context.unregisterReceiver(receiver);
                            } catch (Exception e) {

                            }

                            try {
                                Utils.ensureRouteToHost(context, Settings.get().getMmsc(), Settings.get().getProxy());
                                sendData(bytesToSend);
                            } catch (Exception e) {
                                e.printStackTrace();
                                sendData(bytesToSend);
                            }
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

                try {
                    APN apn = new APN(Settings.get().getMmsc(), Settings.get().getPort(), Settings.get().getProxy());
                    apns.add(apn);

                    String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                    apns.get(0).MMSCenterUrl = mmscUrl;

                    if (apns.get(0).MMSCenterUrl.equals("")) {
                        // attempt to get apns from internal databases, most likely will fail due to insignificant permissions
                        APNHelper helper = new APNHelper(context);
                        apns = helper.getMMSApns();
                    }
                } catch (Exception e) {
                    // error in the apns, none are available most likely causing an index out of bounds
                    // exception. cant send a message, so therefore mark as failed
                    markMmsFailed();
                    return;
                }

                try {
                    // attempts to send the message using given apns
                    Log.v("sending_mms_library", apns.get(0).MMSCenterUrl + " " + apns.get(0).MMSProxy + " " + apns.get(0).MMSPort);
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
                        if (saveMessage) {
                            Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[]{"_id"}, null, null, "date desc");
                            if (query != null && query.moveToFirst()) {
                                String id = query.getString(query.getColumnIndex("_id"));
                                query.close();

                                // move to the sent box
                                ContentValues values = new ContentValues();
                                values.put("msg_box", 2);
                                String where = "_id" + " = '" + id + "'";
                                context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);
                            }
                        }

                        context.sendBroadcast(new Intent(REFRESH));

                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) { /* Receiver not registered */ }

                        // give everything time to finish up, may help the abort being shown after the progress is already 100
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mConnMgr.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE_MMS, "enableMMS");
                                if (Settings.get().getWifiMmsFix()) {
                                    reinstateWifi();
                                }
                            }
                        }, 1000);
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

                            if (Settings.get().getWifiMmsFix()) {
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
        if (Settings.get().getWifiMmsFix()) {
            reinstateWifi();
        }

        if (saveMessage) {
            Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[]{"_id"}, null, null, "date desc");
            if (query != null && query.moveToFirst()) {
                String id = query.getString(query.getColumnIndex("_id"));
                query.close();

                // mark message as failed
                ContentValues values = new ContentValues();
                values.put("msg_box", 5);
                String where = "_id" + " = '" + id + "'";
                context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);
            }
        }

        ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

            @Override
            public void run() {
                context.sendBroadcast(new Intent(REFRESH));
                context.sendBroadcast(new Intent(NOTIFY_SMS_FAILURE));

                // broadcast that mms has failed and you can notify user from there if you would like
                context.sendBroadcast(new Intent(MMS_ERROR));

            }

        });
    }

    private void sendVoiceMessage(final String destAddr, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String rnrse = Settings.get().getRnrSe();
                String account = Settings.get().getAccount();
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
        }).start();
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
        if (saveMessage) {
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
        }

        context.sendBroadcast(new Intent(REFRESH));
        context.sendBroadcast(new Intent(VOICE_FAILED));
    }

    private void successVoice() {
        if (saveMessage) {
            Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

            // mark message as sent successfully
            if (query.moveToFirst()) {
                String id = query.getString(query.getColumnIndex("_id"));
                ContentValues values = new ContentValues();
                values.put("type", "2");
                values.put("read", true);
                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
            }

            query.close();
        }

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
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Activity.TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry : phones.entrySet()) {
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

    private Uri insert(String[] to, MMSPart[] parts, String subject) {
        try {
            Uri destUri = Uri.parse("content://mms");

            Set<String> recipients = new HashSet<String>();
            recipients.addAll(Arrays.asList(to));
            long thread_id = Utils.getOrCreateThreadId(context, recipients);

            // Create a dummy sms
            ContentValues dummyValues = new ContentValues();
            dummyValues.put("thread_id", thread_id);
            dummyValues.put("body", " ");
            Uri dummySms = context.getContentResolver().insert(Uri.parse("content://sms/sent"), dummyValues);

            // Create a new message entry
            long now = System.currentTimeMillis();
            ContentValues mmsValues = new ContentValues();
            mmsValues.put("thread_id", thread_id);
            mmsValues.put("date", now / 1000L);
            mmsValues.put("msg_box", 4);
            //mmsValues.put("m_id", System.currentTimeMillis());
            mmsValues.put("read", true);
            mmsValues.put("sub", subject != null ? subject : "");
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
            mmsValues.put("tr_id", "T" + Long.toHexString(now));
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

        for (int len = 0; (len = is.read(buffer)) != -1; ) {
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
        Uri addrUri = Uri.parse("content://mms/" + id + "/addr");
        Uri res = context.getContentResolver().insert(addrUri, addrValues);

        return res;
    }

    /**
     * A method for checking whether or not a certain message will be sent as mms depending on its contents and the settings
     *
     * @param message is the message that you are checking against
     * @return true if the message will be mms, otherwise false
     */
    public boolean checkMMS(Message message) {
        return message.getImages().length != 0 ||
                (message.getMedia().length != 0 && message.getMediaMimeType() != null) ||
                (Settings.get().getSendLongAsMms() && Utils.getNumPages(message.getText()) > Settings.get().getSendLongAsMmsAfter() && message.getType() != Message.TYPE_VOICE) ||
                (message.getAddresses().length > 1 && Settings.get().getGroup()) ||
                message.getSubject() != null;
    }

    /**
     * @deprecated
     */
    private void reinstateWifi() {
        try {
            context.unregisterReceiver(Settings.get().discon);
        } catch (Exception f) {

        }

        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifi.setWifiEnabled(false);
        wifi.setWifiEnabled(Settings.get().currentWifiState);
        wifi.reconnect();
        Utils.setMobileDataEnabled(context, Settings.get().currentDataState);
    }

    /**
     * @deprecated
     */
    private void revokeWifi(boolean saveState) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (saveState) {
            Settings.get().currentWifi = wifi.getConnectionInfo();
            Settings.get().currentWifiState = wifi.isWifiEnabled();
            wifi.disconnect();
            Settings.get().discon = new DisconnectWifi();
            context.registerReceiver(Settings.get().discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            Settings.get().currentDataState = Utils.isMobileDataEnabled(context);
            Utils.setMobileDataEnabled(context, true);
        } else {
            wifi.disconnect();
            wifi.disconnect();
            Settings.get().discon = new DisconnectWifi();
            context.registerReceiver(Settings.get().discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
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
