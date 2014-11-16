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

package com.android.mms.service.http;

import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import com.klinker.android.logger.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * This is a subclass of {@link org.apache.http.impl.conn.DefaultClientConnectionOperator}
 * which allows us to use a custom name resolver and pick the address type when we resolve
 * the host name to connect.
 */
public class NetworkAwareClientConnectionOperator extends DefaultClientConnectionOperator {
    private static final String TAG = "NetworkAwareClientConnectionOperator";
    private static final PlainSocketFactory staticPlainSocketFactory = new PlainSocketFactory();

    private NameResolver mResolver;
    private boolean mShouldUseIpv6;

    public NetworkAwareClientConnectionOperator(SchemeRegistry schemes) {
        super(schemes);
    }

    public void setNameResolver(NameResolver resolver) {
        mResolver = resolver;
    }

    public void setShouldUseIpv6(boolean value) {
        mShouldUseIpv6 = value;
    }

    /**
     * Resolve name by address type. Only returns IPv6 addresses if required, or IPv4 if not.
     *
     * @param hostName
     * @return The list addresses resolved
     * @throws java.io.IOException
     */
    private ArrayList<InetAddress> resolveHostName(final String hostName) throws IOException {
        final ArrayList<InetAddress> addresses = new ArrayList<InetAddress>();
        for (final InetAddress address : mResolver.getAllByName(hostName)) {
            if (mShouldUseIpv6 && address instanceof Inet6Address) {
                addresses.add(address);
            } else if (!mShouldUseIpv6 && address instanceof Inet4Address){
                addresses.add(address);
            }
        }
        return addresses;
    }

    /**
     * This method is mostly copied from the overridden one in parent. The only change
     * is how we resolve host name.
     */
    @Override
    public void openConnection(OperatedClientConnection conn, HttpHost target, InetAddress local,
            HttpContext context, HttpParams params) throws IOException {
        if (conn == null) {
            throw new IllegalArgumentException
                ("Connection must not be null.");
        }
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }
        // local address may be null
        //@@@ is context allowed to be null?
        if (params == null) {
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        }
        if (conn.isOpen()) {
            throw new IllegalArgumentException
                ("Connection must not be open.");
        }

        final Scheme schm = schemeRegistry.getScheme(target.getSchemeName());
        final SocketFactory sf = schm.getSocketFactory();
        final SocketFactory plain_sf;
        final LayeredSocketFactory layered_sf;
        if (sf instanceof LayeredSocketFactory) {
            plain_sf = staticPlainSocketFactory;
            layered_sf = (LayeredSocketFactory)sf;
        } else {
            plain_sf = sf;
            layered_sf = null;
        }
        // CHANGE FOR MmsService
        ArrayList<InetAddress> addresses = resolveHostName(target.getHostName());

        for (int i = 0; i < addresses.size(); ++i) {
            Log.d(TAG, "NetworkAwareClientConnectionOperator: connecting "
                    + addresses.get(i));
            Socket sock = plain_sf.createSocket();
            conn.opening(sock, target);

            try {
                Socket connsock = plain_sf.connectSocket(sock,
                    addresses.get(i).getHostAddress(),
                    schm.resolvePort(target.getPort()),
                    local, 0, params);
                if (sock != connsock) {
                    sock = connsock;
                    conn.opening(sock, target);
                }
                /*
                 * prepareSocket is called on the just connected
                 * socket before the creation of the layered socket to
                 * ensure that desired socket options such as
                 * TCP_NODELAY, SO_RCVTIMEO, SO_LINGER will be set
                 * before any I/O is performed on the socket. This
                 * happens in the common case as
                 * SSLSocketFactory.createSocket performs hostname
                 * verification which requires that SSL handshaking be
                 * performed.
                 */
                prepareSocket(sock, context, params);
                if (layered_sf != null) {
                    Socket layeredsock = layered_sf.createSocket(sock,
                        target.getHostName(),
                        schm.resolvePort(target.getPort()),
                        true);
                    if (layeredsock != sock) {
                        conn.opening(layeredsock, target);
                    }
                    conn.openCompleted(sf.isSecure(layeredsock), params);
                } else {
                    conn.openCompleted(sf.isSecure(sock), params);
                }
                break;
            // BEGIN android-changed
            //       catch SocketException to cover any kind of connect failure
            } catch (SocketException ex) {
                if (i == addresses.size() - 1) {
                    ConnectException cause = ex instanceof ConnectException
                            ? (ConnectException) ex :
                                (ConnectException) new ConnectException(
                                        ex.getMessage()).initCause(ex);
                    throw new HttpHostConnectException(target, cause);
                }
            // END android-changed
            } catch (ConnectTimeoutException ex) {
                if (i == addresses.size() - 1) {
                    throw ex;
                }
            }
        }
    }
}
