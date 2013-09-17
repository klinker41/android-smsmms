package com.google.android.mms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.widget.Toast;

public class APNHelper {
	
	public APNHelper(final Context context) {
	    this.context = context;
	}   
	
	@SuppressWarnings("unchecked")
	public List<APN> getMMSApns() {
        try {
            final Cursor apnCursor = this.context.getContentResolver().query(Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current"), null, null, null, null);
            if ( apnCursor == null ) {
                return Collections.EMPTY_LIST;
            } else {
                final List<APN> results = new ArrayList<APN>();
                if ( apnCursor.moveToFirst() ) {
                    do {
                        final String type = apnCursor.getString(apnCursor.getColumnIndex(Telephony.Carriers.TYPE));
                        if ( !TextUtils.isEmpty(type) && ( type.equalsIgnoreCase("*") || type.equalsIgnoreCase("mms") ) ) {
                            final String mmsc = apnCursor.getString(apnCursor.getColumnIndex(Telephony.Carriers.MMSC));
                            final String mmsProxy = apnCursor.getString(apnCursor.getColumnIndex(Telephony.Carriers.MMSPROXY));
                            final String port = apnCursor.getString(apnCursor.getColumnIndex(Telephony.Carriers.MMSPORT));
                            final APN apn = new APN();
                            apn.MMSCenterUrl = mmsc;
                            apn.MMSProxy = mmsProxy;
                            apn.MMSPort = port;
                            results.add(apn);

                            Toast.makeText(context, mmsc + " " + mmsProxy + " " + port, Toast.LENGTH_LONG).show();
                        }
                    } while ( apnCursor.moveToNext() );
                }
                apnCursor.close();
                return results;
            }
        } catch (Exception e) {
            // insignificant permissions
            return Collections.EMPTY_LIST;
        }
	}
	
	private Context context;
}
