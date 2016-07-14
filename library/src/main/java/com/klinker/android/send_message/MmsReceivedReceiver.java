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

            DownloadRequest.persist(context, response,
                    new MmsConfig.Overridden(new MmsConfig(context), null),
                    intent.getStringExtra(EXTRA_LOCATION_URL),
                    Utils.getDefaultSubscriptionId(), null);

            Log.v(TAG, "response saved successfully");
            Log.v(TAG, "response length: " + response.length);
            mDownloadFile.delete();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "MMS received, file not found exception", e);
        } catch (IOException e) {
            Log.e(TAG, "MMS received, io exception", e);
        }
    }

}
