/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.klinker.android.send_message;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.mms.service_alt.DownloadRequest;
import com.android.mms.service_alt.MmsConfig;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.util_alt.SqliteWrapper;
import com.klinker.android.logger.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class MmsReceivedReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsReceivedReceiver";

    public static final String MMS_RECEIVED = "com.klinker.android.messaging.MMS_RECEIVED";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_LOCATION_URL = "location_url";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "MMS has finished downloading, persisting it to the database");

        String path = intent.getStringExtra(EXTRA_FILE_PATH);
        Log.v(TAG, path);

        try {
            File mDownloadFile = new File(path);
            final int nBytes = (int) mDownloadFile.length();
            FileInputStream reader = new FileInputStream(mDownloadFile);
            final byte[] response = new byte[nBytes];
            reader.read(response, 0, nBytes);

            Uri uri = DownloadRequest.persist(context, response,
                    new MmsConfig.Overridden(new MmsConfig(context), null),
                    intent.getStringExtra(EXTRA_LOCATION_URL),
                    Utils.getDefaultSubscriptionId(), null);

            //workaroundForFi(context, uri);

            Log.v(TAG, "response saved successfully");
            Log.v(TAG, "response length: " + response.length);
            mDownloadFile.delete();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "MMS received, file not found exception", e);
        } catch (IOException e) {
            Log.e(TAG, "MMS received, io exception", e);
        }
    }

    private void workaroundForFi(Context context, Uri uri) {
        String msgId = uri.getLastPathSegment();
        Uri.Builder builder = Telephony.Mms.CONTENT_URI.buildUpon();

        builder.appendPath(msgId).appendPath("addr");

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                builder.build(), new String[]{Telephony.Mms.Addr.ADDRESS,
                        Telephony.Mms.Addr.CHARSET, Telephony.Mms.Addr.TYPE,
                        Telephony.Mms.Addr._ID},
                null, null, null);

        Set<String> recipients = new HashSet<String>();

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        String from = cursor.getString(0);

                        if (!TextUtils.isEmpty(from)) {
                            byte[] bytes = PduPersister.getBytes(from);
                            int charset = cursor.getInt(1);
                            int type = cursor.getInt(2);
                            long id = cursor.getLong(3);
                            Log.v("MMS_address", new EncodedStringValue(charset, bytes)
                                    .getString() + " " + type + " " + id);

                            if (type == PduHeaders.CC || type == PduHeaders.BCC || type == PduHeaders.FROM || type == PduHeaders.TO) {
                                recipients.add(new EncodedStringValue(charset, bytes).getString());
                            }
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }

        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String carrierName = manager.getNetworkOperatorName();

        if (carrierName.equals("Fi Network")) {
            Log.v(TAG, "on project fi, which hasn't been working for group messages, so work around it");

            String myPhoneNumber = Utils.getMyPhoneNumber(context);
            if (!recipients.contains(myPhoneNumber)) {
                //recipients.add(myPhoneNumber);
            }

            for (String recipient : recipients) {
                Log.v(TAG, "recipient: " + recipient);
            }

            // if group message
            if (recipients.size() > 1) {
                cursor = SqliteWrapper.query(context, context.getContentResolver(),
                        uri, new String[]{Telephony.Mms.THREAD_ID},
                        null, null, null);

                long threadId = -1;
                long actualThreadId = Utils.getOrCreateThreadId(context, recipients);
                if (cursor != null && cursor.moveToFirst()) {
                    threadId = cursor.getLong(0);
                    cursor.close();
                }

                Log.v(TAG, "found " + recipients.size() + " recipients of the message");
                Log.v(TAG, "thread id should be " + actualThreadId + " and actually is " + threadId);

                if (threadId != actualThreadId && Utils.doesThreadIdExist(context, actualThreadId)) {
                    Log.v(TAG, "thread ids do not match, so switching it to the correct thread");
                    ContentValues values = new ContentValues(1);
                    values.put(Telephony.Mms.THREAD_ID, actualThreadId);
                    context.getContentResolver().update(uri, values, null, null);
                }
            }
        }
    }

}
