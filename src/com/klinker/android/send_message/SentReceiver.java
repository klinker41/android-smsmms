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
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;

public class SentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        switch (getResultCode()) {
            case Activity.RESULT_OK:
                Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                // mark message as sent successfully
                if (query != null && query.moveToFirst()) {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "2");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                query.close();

                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                // mark message as failed and give notification to user to tell them
                if (query != null && query.moveToFirst()) {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                context.sendBroadcast(new Intent(Transaction.NOTIFY_SMS_FAILURE));
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                // mark message as failed
                if (query != null && query.moveToFirst()) {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                context.sendBroadcast(new Intent(Transaction.NOTIFY_SMS_FAILURE));

                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                // mark message failed
                if (query != null && query.moveToFirst()) {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                context.sendBroadcast(new Intent(Transaction.NOTIFY_SMS_FAILURE));

                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                // mark message failed
                if (query != null && query.moveToFirst()) {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                context.sendBroadcast(new Intent(Transaction.NOTIFY_SMS_FAILURE));

                break;
        }

        context.sendBroadcast(new Intent("com.klinker.android.send_message.REFRESH"));
    }
}
