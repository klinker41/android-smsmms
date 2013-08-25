/*
 * Copyright 2013 Jacob Klinker
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
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
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.android.mms.transaction.HttpUtils;
import com.google.android.mms.APN;
import com.google.android.mms.APNHelper;
import com.google.android.mms.MMSPart;
import com.google.android.mms.pdu_alt.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Transaction {

    public Settings settings;
    public Context context;
    public static final String GSM_CHARACTERS_REGEX = "^[A-Za-z0-9 \\r\\n@Ł$ĽčéůěňÇŘřĹĺ\u0394_\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039EĆćßÉ!\"#$%&'()*+,\\-./:;<=>?ĄÄÖŃÜ§żäöńüŕ^{}\\\\\\[~\\]|\u20AC]*$";

    public Transaction(Context context) {
        settings = new Settings();
        this.context = context;
    }

    public Transaction(Context context, String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix) {
        settings = new Settings(mmsc, proxy, port, group, wifiMmsFix);
        this.context = context;
    }

    public Transaction(Context context, boolean preferVoice, boolean deliveryReports, boolean split) {
        settings = new Settings(preferVoice, deliveryReports, split);
        this.context = context;
    }

    public Transaction(Context context, String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix, boolean preferVoice, boolean deliveryReports, boolean split) {
        settings = new Settings(mmsc, proxy, port, group, wifiMmsFix, preferVoice, deliveryReports, split);
        this.context = context;
    }

    public Transaction(Context context, String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix, boolean preferVoice, boolean deliveryReports, boolean split, boolean splitCounter, boolean stripUnicode, String signature, boolean sendLongAsMms, int sendLongAsMmsAfter) {
        settings = new Settings(mmsc, proxy, port, group, wifiMmsFix, preferVoice, deliveryReports, split, splitCounter, stripUnicode, signature, sendLongAsMms, sendLongAsMmsAfter);
        this.context = context;
    }

    public Transaction(Context context, Settings settings) {
        this.settings = settings;
        this.context = context;
    }

    public void sendNewMessage(final Message message, final String threadId) {

        if (message.getImages().length != 0 || (settings.getSendLongAsMms() && getNumPages(settings, message.getText()) > settings.getSendLongAsMmsAfter() && !settings.getPreferVoice()) || (message.getAddresses().length > 1 && settings.getGroup())) {
            sendMmsMessage(message.getText(), message.getAddresses(), message.getImages(), threadId);
        } else {
            if (settings.getPreferVoice()) {
                sendVoiceMessage(message.getText(), message.getAddresses());
            } else {
                sendSmsMessage(message.getText(), message.getAddresses(), threadId);
            }
        }

    }

    private void sendSmsMessage(String text, String[] addresses, String threadId) {
        String SENT = "com.klinker.android.send_message.SMS_SENT";
        String DELIVERED = "com.klinker.android.send_message.SMS_DELIVERED";

        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, new Intent(SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, new Intent(DELIVERED), 0);

        ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>();
        ArrayList<PendingIntent> dPI = new ArrayList<PendingIntent>();

        String body = text;

        if (settings.getStripUnicode()) {
            body = StripAccents.stripAccents(body);
        }

        if (!settings.getSignature().equals("")) {
            body += "\n" + settings.getSignature();
        }

        SmsManager smsManager = SmsManager.getDefault();

        if (settings.getSplit()) {
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

            String[] textToSend = splitByLength(body, length, counter);

            for (int i = 0; i < textToSend.length; i++)
            {
                ArrayList<String> parts = smsManager.divideMessage(textToSend[i]);

                for (int j = 0; j < parts.size(); j++)
                {
                    sPI.add(sentPI);
                    dPI.add(deliveredPI);
                }

                for (int j = 0; j < addresses.length; j++) {
                    smsManager.sendMultipartTextMessage(addresses[j], null, parts, sPI, settings.getDeliveryReports() ? dPI : null);
                }
            }
        } else
        {
            ArrayList<String> parts = smsManager.divideMessage(body);

            for (int i = 0; i < parts.size(); i++)
            {
                sPI.add(sentPI);
                dPI.add(deliveredPI);
            }

            try {
                for (int i = 0; i < addresses.length; i++) {
                    smsManager.sendMultipartTextMessage(addresses[i], null, parts, sPI, settings.getDeliveryReports() ? dPI : null);
                }
            } catch (Exception e) {
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

        if (!settings.getSignature().equals(""))
        {
            text += "\n" + settings.getSignature();
        }

        for (int i = 0; i < addresses.length; i++) {
            Calendar cal = Calendar.getInstance();
            ContentValues values = new ContentValues();
            values.put("address", addresses[i]);
            values.put("body", settings.getStripUnicode() ? StripAccents.stripAccents(text) : text);
            values.put("date", cal.getTimeInMillis() + "");
            values.put("read", 1);

            if (threadId == null || addresses.length > 1) {
                threadId = Telephony.Threads.getOrCreateThreadId(context, addresses[i]) + "";
            }

            values.put("thread_id", threadId);
            context.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);
        }
    }

    private void sendMmsMessage(String text, String[] addresses, Bitmap[] image, String threadId) {
        String address = "";

        for (int i = 0; i < addresses.length; i++) {
            address += addresses[i] + " ";
        }

        address = address.trim();

        if (image.length <= 1) {
            insert(("insert-address-token " + address).split(" "), "", image.length != 0 ? Message.bitmapToByteArray(image[0]) : null, text, threadId);

            MMSPart[] parts = new MMSPart[2];

            if (image.length != 0)
            {
                parts[0] = new MMSPart();
                parts[0].Name = "Image";
                parts[0].MimeType = "image/png";
                parts[0].Data = Message.bitmapToByteArray(image[0]);

                if (!text.equals(""))
                {
                    parts[1] = new MMSPart();
                    parts[1].Name = "Text";
                    parts[1].MimeType = "text/plain";
                    parts[1].Data = text.getBytes();
                }
            } else
            {
                parts[0] = new MMSPart();
                parts[0].Name = "Text";
                parts[0].MimeType = "text/plain";
                parts[0].Data = text.getBytes();
            }

            sendMMS(address.split(" "), parts);
        } else {
            ArrayList<MMSPart> data = new ArrayList<MMSPart>();
            ArrayList<byte[]> bytes = new ArrayList<byte[]>();
            ArrayList<String> mimes = new ArrayList<String>();

            for (int i = 0; i < image.length; i++)
            {
                byte[] imageBytes = Message.bitmapToByteArray(image[i]);
                bytes.add(imageBytes);
                mimes.add("image/png");

                MMSPart part = new MMSPart();
                part.MimeType = "image/png";
                part.Name = "Image";
                part.Data = imageBytes;
                data.add(part);
            }

            insert(("insert-address-token " + address).split(" "), "", bytes, mimes, text, threadId);

            MMSPart part = new MMSPart();
            part.Name = "Text";
            part.MimeType = "text/plain";
            part.Data = text.getBytes();
            data.add(part);

            sendMMS(address.split(" "), data.toArray(new MMSPart[data.size()]));
        }
    }

    private void sendVoiceMessage(String text, String[] addresses) {

    }

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

    private String[] splitByLength(String s, int chunkSize, boolean counter)
    {
        int arraySize = (int) Math.ceil((double) s.length() / chunkSize);

        String[] returnArray = new String[arraySize];

        int index = 0;
        for(int i = 0; i < s.length(); i = i+chunkSize)
        {
            if(s.length() - i < chunkSize)
            {
                returnArray[index++] = s.substring(i);
            }
            else
            {
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

    public Uri insert(String[] to, String subject, byte[] imageBytes, String text, String threadId)
    {
        try
        {
            Uri destUri = Uri.parse("content://mms");

            // Get thread id
            String thread_id = threadId;

            if (thread_id == null) {
                Set<String> recipients = new HashSet<String>();
                recipients.addAll(Arrays.asList(to));
                thread_id = Telephony.Threads.getOrCreateThreadId(context, recipients) + "";
            }

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
            mmsValues.put("sub", subject);
            mmsValues.put("sub_cs", 106);
            mmsValues.put("ct_t", "application/vnd.wap.multipart.related");

            if (imageBytes != null)
            {
                mmsValues.put("exp", imageBytes.length);
            } else
            {
                mmsValues.put("exp", 0);
            }

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
            if (imageBytes != null)
            {
                createPartImage(messageId, imageBytes, "image/png");
            }

            createPartText(messageId, text);

            // Create addresses
            for (String addr : to)
            {
                createAddr(messageId, addr);
            }

            //res = Uri.parse(destUri + "/" + messageId);

            // Delete dummy sms
            context.getContentResolver().delete(dummySms, null, null);

            return res;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public Uri insert(String[] to, String subject, ArrayList<byte[]> imageBytes, ArrayList<String> mimeTypes, String text, String threadId)
    {
        try
        {
            Uri destUri = Uri.parse("content://mms");

            // Get thread id
            String thread_id = threadId;

            if (thread_id == null) {
                Set<String> recipients = new HashSet<String>();
                recipients.addAll(Arrays.asList(to));
                thread_id = Telephony.Threads.getOrCreateThreadId(context, recipients) + "";
            }

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
            mmsValues.put("sub", subject);
            mmsValues.put("sub_cs", 106);
            mmsValues.put("ct_t", "application/vnd.wap.multipart.related");

            if (imageBytes != null)
            {
                mmsValues.put("exp", imageBytes.get(0).length);
            } else
            {
                mmsValues.put("exp", 0);
            }

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
            for (int i = 0; i < imageBytes.size(); i++)
            {
                createPartImage(messageId, imageBytes.get(i), mimeTypes.get(i));
            }

            createPartText(messageId, text);

            // Create addresses
            for (String addr : to)
            {
                createAddr(messageId, addr);
            }

            //res = Uri.parse(destUri + "/" + messageId);

            // Delete dummy sms
            context.getContentResolver().delete(dummySms, null, null);

            return res;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private Uri createPartImage(String id, byte[] imageBytes, String mimeType) throws Exception
    {
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
        for (int len=0; (len=is.read(buffer)) != -1;)
        {
            os.write(buffer, 0, len);
        }
        os.close();
        is.close();

        return res;
    }

    private Uri createPartText(String id, String text) throws Exception
    {
        ContentValues mmsPartValue = new ContentValues();
        mmsPartValue.put("mid", id);
        mmsPartValue.put("ct", "text/plain");
        mmsPartValue.put("cid", "<" + System.currentTimeMillis() + ">");
        mmsPartValue.put("text", text);
        Uri partUri = Uri.parse("content://mms/" + id + "/part");
        Uri res = context.getContentResolver().insert(partUri, mmsPartValue);

        return res;
    }

    private Uri createAddr(String id, String addr) throws Exception
    {
        ContentValues addrValues = new ContentValues();
        addrValues.put("address", addr);
        addrValues.put("charset", "106");
        addrValues.put("type", 151); // TO
        Uri addrUri = Uri.parse("content://mms/"+ id +"/addr");
        Uri res = context.getContentResolver().insert(addrUri, addrValues);

        return res;
    }

    public static void setMobileDataEnabled(Context context, boolean enabled) {
        try {
            final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final Class conmanClass = Class.forName(conman.getClass().getName());
            final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
            iConnectivityManagerField.setAccessible(true);
            final Object iConnectivityManager = iConnectivityManagerField.get(conman);
            final Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
            final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);

            setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public static Boolean isMobileDataEnabled(Context context){
        Object connectivityService = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) connectivityService;

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

    private void ensureRouteToHost(String url, String proxy) throws IOException {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

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

    public void sendMMS(final String[] recipient, final MMSPart[] parts)
    {
        if (settings.getWifiMmsFix())
        {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            settings.currentWifiState = wifi.isWifiEnabled();
            settings.currentWifi = wifi.getConnectionInfo();
            wifi.disconnect();
            settings.discon = new DisconnectWifi();
            context.registerReceiver(settings.discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
            settings.currentDataState = isMobileDataEnabled(context);
            setMobileDataEnabled(context, true);
        }

        ConnectivityManager mConnMgr =  (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

        if (result != 0)
        {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            BroadcastReceiver receiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context1, Intent intent) {
                    String action = intent.getAction();

                    if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
                    {
                        return;
                    }

                    @SuppressWarnings("deprecation")
                    NetworkInfo mNetworkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                    if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE))
                    {
                        return;
                    }

                    if (!mNetworkInfo.isConnected())
                    {
                        return;
                    } else
                    {
                        sendData(recipient, parts);

                        context.unregisterReceiver(this);
                    }

                }

            };

            context.registerReceiver(receiver, filter);
        } else
        {
            sendData(recipient, parts);
        }
    }

    public void sendData(final String[] recipients, final MMSPart[] parts)
    {
        new Thread(new Runnable() {

            @Override
            public void run() {

                final SendReq sendRequest = new SendReq();

                for (int i = 0; i < recipients.length; i++)
                {
                    final EncodedStringValue[] phoneNumbers = EncodedStringValue.extract(recipients[i]);

                    if (phoneNumbers != null && phoneNumbers.length > 0)
                    {
                        sendRequest.addTo(phoneNumbers[0]);
                    }
                }

                final PduBody pduBody = new PduBody();

                if (parts != null)
                {
                    for (MMSPart part : parts)
                    {
                        if (part != null)
                        {
                            try
                            {
                                final PduPart partPdu = new PduPart();
                                partPdu.setName(part.Name.getBytes());
                                partPdu.setContentType(part.MimeType.getBytes());
                                partPdu.setData(part.Data);
                                pduBody.addPart(partPdu);
                            } catch (Exception e)
                            {

                            }
                        }
                    }
                }

                sendRequest.setBody(pduBody);

                final PduComposer composer = new PduComposer(context, sendRequest);
                final byte[] bytesToSend = composer.make();

                List<APN> apns = new ArrayList<APN>();

                try
                {
                    APNHelper helper = new APNHelper(context);
                    apns = helper.getMMSApns();
                } catch (Exception e)
                {
                    Log.v("apn_error", "could not retrieve system apns, using manual values instead");
                    APN apn = new APN(settings.getMmsc(), settings.getPort(), settings.getProxy());
                    apns.add(apn);

                    String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                    apns.get(0).MMSCenterUrl = mmscUrl;
                }

                try {
                    Log.v("apns_to_use", apns.get(0).MMSCenterUrl + " " + apns.get(0).MMSPort + " " + apns.get(0).MMSProxy);
                    ensureRouteToHost(apns.get(0).MMSCenterUrl, apns.get(0).MMSProxy);
                    HttpUtils.httpConnection(context, 4444L, apns.get(0).MMSCenterUrl, bytesToSend, HttpUtils.HTTP_POST_METHOD, !TextUtils.isEmpty(apns.get(0).MMSProxy), apns.get(0).MMSProxy, Integer.parseInt(apns.get(0).MMSPort));

                    IntentFilter filter = new IntentFilter();
                    filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                    BroadcastReceiver receiver = new BroadcastReceiver() {

                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[] {"_id"}, null, null, "date desc");
                            query.moveToFirst();
                            String id = query.getString(query.getColumnIndex("_id"));
                            query.close();

                            ContentValues values = new ContentValues();
                            values.put("msg_box", 2);
                            String where = "_id" + " = '" + id + "'";
                            context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);

                            context.sendBroadcast(new Intent("com.klinker.android.send_message.REFRESH"));
                            context.unregisterReceiver(this);
                            if (settings.getWifiMmsFix())
                            {
                                try {
                                    context.unregisterReceiver(settings.discon);
                                } catch (Exception e) {

                                }

                                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                                wifi.setWifiEnabled(false);
                                wifi.setWifiEnabled(settings.currentWifiState);
                                wifi.reconnect();
                                setMobileDataEnabled(context, settings.currentDataState);
                            }
                        }

                    };

                    context.registerReceiver(receiver, filter);
                } catch (Exception e) {
                    e.printStackTrace();

                    if (settings.getWifiMmsFix())
                    {
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

                    Cursor query = context.getContentResolver().query(Uri.parse("content://mms"), new String[] {"_id"}, null, null, "date desc");
                    query.moveToFirst();
                    String id = query.getString(query.getColumnIndex("_id"));
                    query.close();

                    ContentValues values = new ContentValues();
                    values.put("msg_box", 5);
                    String where = "_id" + " = '" + id + "'";
                    context.getContentResolver().update(Uri.parse("content://mms"), values, where, null);

                    ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                        @Override
                        public void run() {
                            context.sendBroadcast(new Intent("com.klinker.android.send_message.REFRESH"));
                            context.sendBroadcast(new Intent("com.klinker.android.send_message.MMS_ERROR"));

                        }

                    });
                }

            }

        }).start();

    }
}
