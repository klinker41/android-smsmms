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

import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.google.android.mms.util_alt.SqliteWrapper;

import com.android.mms.service.exception.MmsHttpException;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

/**
 * Request to download an MMS
 */
public class DownloadRequest extends MmsRequest {
    private static final String TAG = "DownloadRequest";
    
    private static final String LOCATION_SELECTION =
            Telephony.Mms.MESSAGE_TYPE + "=? AND " + Telephony.Mms.CONTENT_LOCATION + " =?";

    private final String mLocationUrl;
    private final PendingIntent mDownloadedIntent;
    private final Uri mContentUri;

    public DownloadRequest(RequestManager manager, String locationUrl,
            Uri contentUri, PendingIntent downloadedIntent, String creator,
            Bundle configOverrides) {
        super(manager, null/*messageUri*/, creator, configOverrides);
        mLocationUrl = locationUrl;
        mDownloadedIntent = downloadedIntent;
        mContentUri = contentUri;
    }

    @Override
    protected byte[] doHttp(Context context, MmsNetworkManager netMgr, ApnSettings apn)
            throws MmsHttpException {
        return doHttpForResolvedAddresses(context,
                netMgr,
                mLocationUrl,
                null/*pdu*/,
                HttpUtils.HTTP_GET_METHOD,
                apn);
    }

    @Override
    protected PendingIntent getPendingIntent() {
        return mDownloadedIntent;
    }

    @Override
    protected int getRunningQueue() {
        return 1;
    }

    @Override
    protected void updateStatus(Context context, int result, byte[] response) {
        if (mRequestManager.getAutoPersistingPref()) {
            storeInboxMessage(context, result, response);
        }
    }

    /**
     * Transfer the received response to the caller (for download requests write to content uri)
     *
     * @param fillIn the intent that will be returned to the caller
     * @param response the pdu to transfer
     */
    @Override
    protected boolean transferResponse(Intent fillIn, final byte[] response) {
        return mRequestManager.writePduToContentUri(mContentUri, response);
    }

    private void storeInboxMessage(Context context, int result, byte[] response) {
        if (response == null || response.length < 1) {
            return;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            final GenericPdu pdu = (new PduParser(response)).parse();
            if (pdu == null || !(pdu instanceof RetrieveConf)) {
                Log.e(TAG, "DownloadRequest.updateStatus: invalid parsed PDU");
                return;
            }
            // Store the downloaded message
            final PduPersister persister = PduPersister.getPduPersister(context);
            mMessageUri = persister.persist(
                    pdu,
                    Telephony.Mms.Inbox.CONTENT_URI,
                    true/*createThreadId*/,
                    true/*groupMmsEnabled*/,
                    null/*preOpenedFiles*/);
            if (mMessageUri == null) {
                Log.e(TAG, "DownloadRequest.updateStatus: can not persist message");
                return;
            }
            // Update some of the properties of the message
            ContentValues values = new ContentValues(5);
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.READ, 0);
            values.put(Telephony.Mms.SEEN, 0);
            if (!TextUtils.isEmpty(mCreator)) {
                values.put(Telephony.Mms.CREATOR, mCreator);
            }
            //values.put(Telephony.Mms.SUB_ID, mSubId);
            if (SqliteWrapper.update(
                    context,
                    context.getContentResolver(),
                    mMessageUri,
                    values,
                    null/*where*/,
                    null/*selectionArg*/) != 1) {
                Log.e(TAG, "DownloadRequest.updateStatus: can not update message");
            }
            // Delete the corresponding NotificationInd
            SqliteWrapper.delete(context,
                    context.getContentResolver(),
                    Telephony.Mms.CONTENT_URI,
                    LOCATION_SELECTION,
                    new String[]{
                            Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                            mLocationUrl
                    }
            );
        } catch (MmsException e) {
            Log.e(TAG, "DownloadRequest.updateStatus: can not persist message", e);
        } catch (SQLiteException e) {
            Log.e(TAG, "DownloadRequest.updateStatus: can not update message", e);
        } catch (RuntimeException e) {
            Log.e(TAG, "DownloadRequest.updateStatus: can not parse response", e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    protected boolean prepareForHttpRequest(Context context) {
        return true;
    }

    /**
     * Try downloading via the carrier app by sending intent.
     *
     * @param context The context
     */
    public void tryDownloadingByCarrierApp(Context context) {
//        TelephonyManager telephonyManager =
//                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        Intent intent = new Intent(Telephony.Mms.Intents.MMS_DOWNLOAD_ACTION);
//        List<String> carrierPackages = telephonyManager.getCarrierPackageNamesForIntent(
//                intent);
//
//        if (carrierPackages == null || carrierPackages.size() != 1) {
//            mRequestManager.addRunning(this);
//        } else {
//            intent.setPackage(carrierPackages.get(0));
//            intent.putExtra(Telephony.Mms.Intents.EXTRA_MMS_LOCATION_URL, mLocationUrl);
//            intent.putExtra(Telephony.Mms.Intents.EXTRA_MMS_CONTENT_URI, mContentUri);
//            intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
//            context.sendOrderedBroadcastAsUser(
//                    intent,
//                    UserHandle.OWNER,
//                    android.Manifest.permission.RECEIVE_MMS,
//                    AppOpsManager.OP_RECEIVE_MMS,
//                    mCarrierAppResultReceiver,
//                    null/*scheduler*/,
//                    Activity.RESULT_CANCELED,
//                    null/*initialData*/,
//                    null/*initialExtras*/);
//        }
    }

    @Override
    protected void revokeUriPermission(Context context) {
        context.revokeUriPermission(mContentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
}
