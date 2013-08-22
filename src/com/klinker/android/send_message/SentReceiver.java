package com.klinker.android.send_message;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;

public class SentReceiver extends BroadcastReceiver {

	   @Override 
	   public void onReceive(Context context, Intent intent) {
		   
		   switch (getResultCode())
           {
               case Activity.RESULT_OK:
                   Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                   if (query.moveToFirst())
                   {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "2");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                   }

                   query.close();

                   break;
               case SmsManager.RESULT_ERROR_GENERIC_FAILURE:

                    try
                    {
                        Thread.sleep(500);
                    } catch (Exception e)
                    {

                    }

                    query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                       if (query.moveToFirst())
                       {
                        String id = query.getString(query.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("type", "5");
                        values.put("read", true);
                        context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                       }

                    NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(context)
                       .setSmallIcon(R.drawable.ic_alert)
                       .setContentTitle("Error")
                       .setContentText("Could not send message");

                    mBuilder.setAutoCancel(true);
                    long[] pattern = {0L, 400L, 100L, 400L};
                    mBuilder.setVibrate(pattern);
                    mBuilder.setLights(0xFFffffff, 1000, 2000);

                    NotificationManager mNotificationManager =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                    Notification notification = mBuilder.build();
                    mNotificationManager.notify(1, notification);
                    break;
           case SmsManager.RESULT_ERROR_NO_SERVICE:
                try
                {
                    Thread.sleep(500);
                } catch (Exception e)
                {

                }

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                if (query.moveToFirst())
                {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }

                break;
           case SmsManager.RESULT_ERROR_NULL_PDU:
                try
                {
                    Thread.sleep(500);
                } catch (Exception e)
                {

                }

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);
               
                if (query.moveToFirst())
                {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }
               
                break;
           case SmsManager.RESULT_ERROR_RADIO_OFF:
                try
                {
                    Thread.sleep(500);
                } catch (Exception e)
                {

                }

                query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                if (query.moveToFirst())
                {
                    String id = query.getString(query.getColumnIndex("_id"));
                    ContentValues values = new ContentValues();
                    values.put("type", "5");
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                }
               
                break;
           }

           context.sendBroadcast(new Intent("com.klinker.android.send_message.REFRESH"));
	   } 
	}
