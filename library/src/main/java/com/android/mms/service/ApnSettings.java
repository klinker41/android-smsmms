/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.android.mms.service.exception.ApnException;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.net.NetworkUtilsHelper;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import com.klinker.android.logger.Log;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * APN settings used for MMS transactions
 */
public class ApnSettings {
    private static final String TAG = "ApnSettings";

    // MMSC URL
    private final String mServiceCenter;
    // MMSC proxy address
    private final String mProxyAddress;
    // MMSC proxy port
    private final int mProxyPort;

    private static final String[] APN_PROJECTION = {
            Telephony.Carriers.TYPE,            // 0
            Telephony.Carriers.MMSC,            // 1
            Telephony.Carriers.MMSPROXY,        // 2
            Telephony.Carriers.MMSPORT          // 3
    };
    private static final int COLUMN_TYPE         = 0;
    private static final int COLUMN_MMSC         = 1;
    private static final int COLUMN_MMSPROXY     = 2;
    private static final int COLUMN_MMSPORT      = 3;


    /**
     * Load APN settings from system
     *
     * @param context
     * @param apnName the optional APN name to match
     */
    public static ApnSettings load(Context context, String apnName)
            throws ApnException {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mmsc = sharedPrefs.getString("mmsc_url", "");
        if (!TextUtils.isEmpty(mmsc)) {
            String mmsProxy = sharedPrefs.getString("mms_proxy", "");
            String mmsPort = sharedPrefs.getString("mms_port", "");
            return new ApnSettings(mmsc, mmsProxy, parsePort(mmsPort));
        }

        Log.v(TAG, "ApnSettings: apnName " + apnName);
        // TODO: CURRENT semantics is currently broken in telephony. Revive this when it is fixed.
        //String selection = Telephony.Carriers.CURRENT + " IS NOT NULL";
        String selection = null;
        String[] selectionArgs = null;
        apnName = apnName != null ? apnName.trim() : null;
        if (!TextUtils.isEmpty(apnName)) {
            //selection += " AND " + Telephony.Carriers.APN + "=?";
            selection = Telephony.Carriers.APN + "=?";
            selectionArgs = new String[]{ apnName };
        }
        Cursor cursor = null;
        try {
            cursor = SqliteWrapper.query(
                    context,
                    context.getContentResolver(),
                    Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "/subId/" + 1),
                    APN_PROJECTION,
                    selection,
                    selectionArgs,
                    null/*sortOrder*/);
            if (cursor != null) {
                String mmscUrl = null;
                String proxyAddress = null;
                int proxyPort = -1;
                while (cursor.moveToNext()) {
                    // Read values from APN settings
                    if (isValidApnType(
                            cursor.getString(COLUMN_TYPE), "mms")) {
                        mmscUrl = trimWithNullCheck(cursor.getString(COLUMN_MMSC));
                        if (TextUtils.isEmpty(mmscUrl)) {
                            continue;
                        }
                        mmscUrl = NetworkUtilsHelper.trimV4AddrZeros(mmscUrl);
                        try {
                            new URI(mmscUrl);
                        } catch (URISyntaxException e) {
                            throw new ApnException("Invalid MMSC url " + mmscUrl);
                        }
                        proxyAddress = trimWithNullCheck(cursor.getString(COLUMN_MMSPROXY));
                        if (!TextUtils.isEmpty(proxyAddress)) {
                            proxyAddress = NetworkUtilsHelper.trimV4AddrZeros(proxyAddress);
                            final String portString =
                                    trimWithNullCheck(cursor.getString(COLUMN_MMSPORT));
                            if (!TextUtils.isEmpty(portString)) {
                                try {
                                    proxyPort = Integer.parseInt(portString);
                                } catch (NumberFormatException e) {
                                    Log.e(TAG, "Invalid port " + portString);
                                    throw new ApnException("Invalid port " + portString);
                                }
                            }
                        }
                        return new ApnSettings(mmscUrl, proxyAddress, proxyPort);
                    }
                }

            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return new ApnSettings("", "", 80);
    }

    private static String trimWithNullCheck(String value) {
        return value != null ? value.trim() : null;
    }

    public ApnSettings(String mmscUrl, String proxyAddr, int proxyPort) {
        mServiceCenter = mmscUrl;
        mProxyAddress = proxyAddr;
        mProxyPort = proxyPort;
   }

    public String getMmscUrl() {
        return mServiceCenter;
    }

    public String getProxyAddress() {
        return mProxyAddress;
    }

    public int getProxyPort() {
        return mProxyPort;
    }

    public boolean isProxySet() {
        return !TextUtils.isEmpty(mProxyAddress);
    }

    private static boolean isValidApnType(String types, String requestType) {
        // If APN type is unspecified, assume APN_TYPE_ALL.
        if (TextUtils.isEmpty(types)) {
            return true;
        }
        for (String type : types.split(",")) {
            type = type.trim();
            if (type.equals(requestType) || type.equals("*")) {
                return true;
            }
        }
        return false;
    }

    private static int parsePort(String port) {
        if (TextUtils.isEmpty(port)) {
            return 80;
        } else {
            return Integer.parseInt(port);
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("APN:");
        sb.append(" mmsc=").append(mServiceCenter);
        sb.append(" proxy=").append(mProxyAddress);
        sb.append(" port=").append(mProxyPort);
        return sb.toString();
    }
}
