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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    // characters to compare against when checking for 160 character sending compatibility
    public static final String GSM_CHARACTERS_REGEX = "^[A-Za-z0-9 \\r\\n@Ł$ĽčéůěňÇŘřĹĺ\u0394_\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039EĆćßÉ!\"#$%&'()*+,\\-./:;<=>?ĄÄÖŃÜ§żäöńüŕ^{}\\\\\\[~\\]|\u20AC]*$";

    public static final String SMS_SENT = "com.klinker.android.send_message.SMS_SENT";
    public static final String SMS_DELIVERED = "com.klinker.android.send_message.SMS_DELIVERED";
    public static final String MMS_ERROR = "com.klinker.android.send_message.MMS_ERROR";
    public static final String REFRESH = "com.klinker.android.send_message.REFRESH";
    public static final String MMS_PROGRESS = "com.klinker.android.send_message.MMS_PROGRESS";
    public static final String VOICE_FAILED = "com.klinker.android.send_message.VOICE_FAILED";
    public static final String VOICE_TOKEN = "com.klinker.android.send_message.RNRSE";

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
        if (message.getImages().length != 0 || (settings.getSendLongAsMms() && getNumPages(settings, message.getText()) > settings.getSendLongAsMmsAfter() && !settings.getPreferVoice()) || (message.getAddresses().length > 1 && settings.getGroup())) {
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

            String patternStr = "[^" + GSM_CHARACTERS_REGEX + "]";
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
        sendMMS(getBytes(address.split(" "), data.toArray(new MMSPart[data.size()])));
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
            sendRequest.setFrom(new EncodedStringValue(getMyPhoneNumber(context)));
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
            e.printStackTrace();
            // something went wrong saving... :(
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

    // returns the number of pages in the SMS based on settings and the length of string

    /**
     * Gets the number of pages in the SMS based on settings and the length of string
     * @param settings is the settings object to check against
     * @param text is the text from the message object to be sent
     * @return the number of pages required to hold message
     */
    public static int getNumPages(Settings settings, String text) {
        int length = text.length();

        if (!settings.getSignature().equals("")) {
            length += ("\n" + settings.getSignature()).length();
        }

        String patternStr = "[^" + GSM_CHARACTERS_REGEX + "]";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);

        int size = 160;

        if (matcher.find() && !settings.getStripUnicode()) {
            size = 70;
        }

        int pages = 1;

        while (length > size) {
            length-=size;
            pages++;
        }

        return pages;
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

    /**
     * Toggles mobile data
     * @param context is the context of the activity or service
     * @param enabled is whether to enable or disable data
     */
    public static void setMobileDataEnabled(Context context, boolean enabled) {
        try {
            ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Class conmanClass = Class.forName(conman.getClass().getName());
            Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
            iConnectivityManagerField.setAccessible(true);
            Object iConnectivityManager = iConnectivityManagerField.get(conman);
            Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
            Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);

            setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // checks whether or not mobile data is already enabled

    /**
     * Checks whether or not mobile data is enabled and returns the result
     * @param context is the context of the activity or service
     * @return true if data is enabled or false if disabled
     */
    public static Boolean isMobileDataEnabled(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            Class<?> c = Class.forName(cm.getClass().getName());
            Method m = c.getDeclaredMethod("getMobileDataEnabled");
            m.setAccessible(true);
            return (Boolean)m.invoke(cm);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Ensures that the host MMSC is reachable
     * @param context is the context of the activity or service
     * @param url is the MMSC to check
     * @param proxy is the proxy of the APN to check
     * @throws IOException when route cannot be established
     */
    public static void ensureRouteToHost(Context context, String url, String proxy) throws IOException {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        connMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

        int inetAddr;
        if (!proxy.equals("")) {
            String proxyAddr = proxy;
            inetAddr = lookupHost(proxyAddr);
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.TYPE_MOBILE_MMS, inetAddr)) {
                    throw new IOException("Cannot establish route to proxy " + inetAddr);
                }
            }
        } else {
            Uri uri = Uri.parse(url);
            inetAddr = lookupHost(uri.getHost());
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.TYPE_MOBILE_MMS, inetAddr)) {
                    throw new IOException("Cannot establish route to " + inetAddr + " for " + url);
                }
            }
        }
    }

    /**
     * Ensures that the host is reachable
     * @param hostname the Proxy to check
     * @return a proxy without leading zeros
     */
    public static int lookupHost(String hostname) {
        InetAddress inetAddress;

        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }

        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                |  (addrBytes[0] & 0xff);

        return addr;
    }

    private boolean alreadySending = false;

    private void sendMMS(final byte[] bytesToSend) {
        revokeWifi(true);

        // enable mms connection to mobile data
        mConnMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        int result = beginMmsConnectivity();

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
                        alreadySending = true;
                        sendData(bytesToSend);

                        context.unregisterReceiver(this);
                    }

                }

            };

            context.registerReceiver(receiver, filter);

            // try sending after 3 seconds anyways if for some reason the receiver doesn't work
            Looper.prepare();
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
            }, 2000);
        } else {
            // mms connection already active, so send the message
            sendData(bytesToSend);
        }
    }

    private void sendData(final byte[] bytesToSend) {
        // be sure this is running on new thread, not UI
        new Thread(new Runnable() {

            @Override
            public void run() {
                List<APN> apns = new ArrayList<APN>();

                try {
                    // attempt to get apns from internal databases, most likely will fail due to insignificant permissions
                    APNHelper helper = new APNHelper(context);
                    apns = helper.getMMSApns();
                } catch (Exception e) {
                    Log.v("apn_error", "could not retrieve system apns, using manual values instead");
                    // sets up manual apns from our settings object which we will be using
                    APN apn = new APN(settings.getMmsc(), settings.getPort(), settings.getProxy());
                    apns.add(apn);

                    String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                    apns.get(0).MMSCenterUrl = mmscUrl;
                }

                try {
                    // attempts to send the message using given apns
                    trySending(apns.get(0), bytesToSend, 0);
                } catch (Exception e) {
                    // some type of apn error, so notify user of failure
                    context.sendBroadcast(new Intent(MMS_ERROR));
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
                                reinstateWifi();
                            }
                        }, 5000);
                    } else if (progress == ProgressCallbackEntity.PROGRESS_ABORT) {
                        // This seems to get called only after the progress has reached 100 and then something else goes wrong, so here we will try and send again and see if it works
                        Log.v("sending_mms_library", "sending aborted for some reason...");
                        context.unregisterReceiver(this);
                        revokeWifi(false);

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

            };

            context.registerReceiver(receiver, filter);

            // This is where the actual post request is made to send the bytes we previously created through the given apns
            Log.v("sending_mms_library", "attempt: " + numRetries);
            ensureRouteToHost(context, apns.MMSCenterUrl, apns.MMSProxy);
            HttpUtils.httpConnection(context, 4444L, apns.MMSCenterUrl, bytesToSend, HttpUtils.HTTP_POST_METHOD, !TextUtils.isEmpty(apns.MMSProxy), apns.MMSProxy, Integer.parseInt(apns.MMSPort));
        } catch (IOException e) {
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

    // FIXME again with the wifi problems... should not have to do this at all
    private void reinstateWifi() {
        if (settings.getWifiMmsFix()) {
            try {
                context.unregisterReceiver(settings.discon);
            } catch (Exception f) {

            }

            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifi.setWifiEnabled(false);
            wifi.setWifiEnabled(settings.currentWifiState);
            wifi.reconnect();
            setMobileDataEnabled(context, settings.currentDataState);
        }
    }

    // FIXME it should not be required to disable wifi and enable mobile data manually, but I have found no way to use the two at the same time
    private void revokeWifi(boolean saveState) {
        if (settings.getWifiMmsFix()) {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

            if (saveState) {
                settings.currentWifi = wifi.getConnectionInfo();
                settings.currentWifiState = wifi.isWifiEnabled();
                wifi.disconnect();
                settings.discon = new DisconnectWifi();
                context.registerReceiver(settings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
                settings.currentDataState = isMobileDataEnabled(context);
                setMobileDataEnabled(context, true);
            } else {
                wifi.disconnect();
                wifi.disconnect();
                settings.discon = new DisconnectWifi();
                context.registerReceiver(settings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
                setMobileDataEnabled(context, true);
            }
        }
    }

    private int beginMmsConnectivity() {
        int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

        return result;
    }

    private void markMmsFailed() {
        // if it still fails, then mark message as failed
        reinstateWifi();

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
            authToken = getAuthToken(account, context);

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

    /**
     * Function for getting the weird auth token used to send or receive google voice messages
     * @param account is the string of the account name to get the auth token for
     * @param context is the context of the activity or service
     * @return a string of the auth token to be saved for later
     * @throws IOException
     * @throws OperationCanceledException
     * @throws AuthenticatorException
     */
    public static String getAuthToken(String account, Context context) throws IOException, OperationCanceledException, AuthenticatorException {
        Bundle bundle = AccountManager.get(context).getAuthToken(new Account(account, "com.google"), "grandcentral", true, null, null).getResult();
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
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

    /**
     * Gets the current users phone number
     * @param context is the context of the activity or service
     * @return a string of the phone number on the device
     */
    public static String getMyPhoneNumber(Context context){
        TelephonyManager mTelephonyMgr;
        mTelephonyMgr = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        return mTelephonyMgr.getLine1Number();
    }
}
