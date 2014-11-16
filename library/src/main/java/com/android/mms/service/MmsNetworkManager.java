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

import com.android.mms.service.exception.MmsNetworkException;
import com.android.mms.service.http.NameResolver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.SystemClock;
import android.provider.Settings;
import com.klinker.android.logger.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Manages the MMS network connectivity
 *
 * TODO this is the class that does not support backporting to api 14, everything else is good
 * would need to backport connectivitymanager and network at the very least for this to work
 */
public class MmsNetworkManager implements NameResolver {
    private static final String TAG = "MmsNetworkManager";
    // Timeout used to call ConnectivityManager.requestNetwork
    private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;
    // Wait timeout for this class, a little bit longer than the above timeout
    // to make sure we don't bail prematurely
    private static final int NETWORK_ACQUIRE_TIMEOUT_MILLIS =
            NETWORK_REQUEST_TIMEOUT_MILLIS + (5 * 1000);

    private Context mContext;
    // The requested MMS {@link android.net.Network} we are holding
    // We need this when we unbind from it. This is also used to indicate if the
    // MMS network is available.
    private Network mNetwork;
    // The current count of MMS requests that require the MMS network
    // If mMmsRequestCount is 0, we should release the MMS network.
    private int mMmsRequestCount;

    // This is really just for using the capability
    private NetworkRequest mNetworkRequest = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
            .build();

    // The callback to register when we request MMS network
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private ConnectivityManager mConnectivityManager;

    // TODO: we need to re-architect this when we support MSIM, like maybe one manager for each SIM?
    public MmsNetworkManager(Context context) {
        mContext = context;
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
        mConnectivityManager = null;
    }

    public Network getNetwork() {
        synchronized (this) {
            return mNetwork;
        }
    }

    /**
     * Acquire the MMS network
     *
     * @throws com.android.mms.service.exception.MmsNetworkException if we fail to acquire it
     */
    public void acquireNetwork() throws MmsNetworkException {
        if (inAirplaneMode()) {
            // Fast fail airplane mode
            throw new MmsNetworkException("In airplane mode");
        }
        synchronized (this) {
            mMmsRequestCount += 1;
            if (mNetwork != null) {
                // Already available
                Log.d(TAG, "MmsNetworkManager: already available");
                return;
            }
            Log.d(TAG, "MmsNetworkManager: start new network request");
            // Not available, so start a new request
            newRequest();
            final long shouldEnd = SystemClock.elapsedRealtime() + NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            long waitTime = NETWORK_ACQUIRE_TIMEOUT_MILLIS;
            while (waitTime > 0) {
                try {
                    this.wait(waitTime);
                } catch (InterruptedException e) {
                    Log.w(TAG, "MmsNetworkManager: acquire network wait interrupted");
                }
                if (mNetwork != null) {
                    // Success
                    return;
                }
                // Calculate remaining waiting time to make sure we wait the full timeout period
                waitTime = shouldEnd - SystemClock.elapsedRealtime();
            }
            // Timed out, so release the request and fail
            Log.d(TAG, "MmsNetworkManager: timed out");
            releaseRequest(mNetworkCallback);
            resetLocked();
            throw new MmsNetworkException("Acquiring network timed out");
        }
    }

    /**
     * Release the MMS network when nobody is holding on to it.
     */
    public void releaseNetwork() {
        synchronized (this) {
            if (mMmsRequestCount > 0) {
                mMmsRequestCount -= 1;
                Log.d(TAG, "MmsNetworkManager: release, count=" + mMmsRequestCount);
                if (mMmsRequestCount < 1) {
                    releaseRequest(mNetworkCallback);
                    resetLocked();
                }
            }
        }
    }

    /**
     * Start a new {@link android.net.NetworkRequest} for MMS
     */
    private void newRequest() {
        final ConnectivityManager connectivityManager = getConnectivityManager();
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                Log.d(TAG, "NetworkCallbackListener.onAvailable: network=" + network);
                synchronized (MmsNetworkManager.this) {
                    mNetwork = network;
                    MmsNetworkManager.this.notifyAll();
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.d(TAG, "NetworkCallbackListener.onLost: network=" + network);
                synchronized (MmsNetworkManager.this) {
                    releaseRequest(this);
                    if (mNetworkCallback == this) {
                        resetLocked();
                    }
                    MmsNetworkManager.this.notifyAll();
                }
            }

//            @Override
//            public void onUnavailable() {
//                super.onUnavailable();
//                Log.d(TAG, "NetworkCallbackListener.onUnavailable");
//                synchronized (MmsNetworkManager.this) {
//                    releaseRequest(this);
//                    if (mNetworkCallback == this) {
//                        resetLocked();
//                    }
//                    MmsNetworkManager.this.notifyAll();
//                }
//            }
        };
        connectivityManager.requestNetwork(
                mNetworkRequest, mNetworkCallback);
    }

    /**
     * Release the current {@link android.net.NetworkRequest} for MMS
     *
     * @param callback the {@link android.net.ConnectivityManager.NetworkCallback} to unregister
     */
    private void releaseRequest(ConnectivityManager.NetworkCallback callback) {
        if (callback != null) {
            final ConnectivityManager connectivityManager = getConnectivityManager();
            connectivityManager.unregisterNetworkCallback(callback);
        }
    }

    /**
     * Reset the state
     */
    private void resetLocked() {
        mNetworkCallback = null;
        mNetwork = null;
        mMmsRequestCount = 0;
    }

    @Override
    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        synchronized (this) {
            if (mNetwork != null) {
                return mNetwork.getAllByName(host);
            }
            return new InetAddress[0];
        }
    }

    private ConnectivityManager getConnectivityManager() {
        if (mConnectivityManager == null) {
            mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
        }
        return mConnectivityManager;
    }

    private boolean inAirplaneMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
