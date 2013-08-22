package com.klinker.android.send_message;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

public class DeliveredReceiver extends BroadcastReceiver {

	   @Override 
	   public void onReceive(Context context, Intent intent) {
       	   switch (getResultCode())
           {
               case Activity.RESULT_OK:
                    Toast.makeText(context, R.string.message_delivered, Toast.LENGTH_LONG).show();

                    Cursor query = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                    if (query.moveToFirst())
                    {
                        String id = query.getString(query.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("status", "0");
                        values.put("read", true);
                        context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                    }

                    query.close();
                    break;
               case Activity.RESULT_CANCELED:
                    Toast.makeText(context, R.string.message_not_delivered, Toast.LENGTH_LONG).show();

                    Cursor query2 = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                    if (query2.moveToFirst())
                    {
                        String id = query2.getString(query2.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("status", "64");
                        values.put("read", true);
                        context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                    }

                    query2.close();
                    break;
           }

           context.sendBroadcast(new Intent("com.klinker.android.send_message.REFRESH"));
	   } 
	}
