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

import com.android.mms.service.exception.MmsHttpException;
import com.android.mms.service.http.NameResolver;
import com.android.mms.service.http.NetworkAwareHttpClient;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.text.TextUtils;
import com.klinker.android.logger.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP utils to make HTTP request to MMSC
 */
public class HttpUtils {
    private static final String TAG = "HttpUtils";

    public static final int HTTP_POST_METHOD = 1;
    public static final int HTTP_GET_METHOD = 2;

    // Definition for necessary HTTP headers.
    private static final String HDR_KEY_ACCEPT = "Accept";
    private static final String HDR_KEY_ACCEPT_LANGUAGE = "Accept-Language";

    private static final String HDR_VALUE_ACCEPT =
        "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic";

    private HttpUtils() {
        // To forbidden instantiate this class.
    }

    /**
     * A helper method to send or retrieve data through HTTP protocol.
     *
     * @param url The URL used in a GET request. Null when the method is
     *         HTTP_POST_METHOD.
     * @param pdu The data to be POST. Null when the method is HTTP_GET_METHOD.
     * @param method HTTP_POST_METHOD or HTTP_GET_METHOD.
     * @param isProxySet If proxy is set
     * @param proxyHost The host of the proxy
     * @param proxyPort The port of the proxy
     * @param resolver The custom name resolver to use
     * @param useIpv6 If we should use IPv6 address when the HTTP client resolves the host name
     * @param mmsConfig The MmsConfig to use
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws com.android.mms.service.exception.MmsHttpException if HTTP request gets error response (&gt;=400)
     */
    public static byte[] httpConnection(Context context, String url, byte[] pdu, int method,
            boolean isProxySet, String proxyHost, int proxyPort, NameResolver resolver,
            boolean useIpv6, MmsConfig.Overridden mmsConfig) throws MmsHttpException {
        final String methodString = getMethodString(method);
        Log.v(TAG, "HttpUtils: request param list\n"
                + "url=" + url + "\n"
                + "method=" + methodString + "\n"
                + "isProxySet=" + isProxySet + "\n"
                + "proxyHost=" + proxyHost + "\n"
                + "proxyPort=" + proxyPort + "\n"
                + "size=" + (pdu != null ? pdu.length : 0));

        NetworkAwareHttpClient client = null;
        try {
            // Make sure to use a proxy which supports CONNECT.
            URI hostUrl = new URI(url);
            HttpHost target = new HttpHost(hostUrl.getHost(), hostUrl.getPort(),
                    HttpHost.DEFAULT_SCHEME_NAME);
            client = createHttpClient(context, resolver, useIpv6, mmsConfig);
            HttpRequest req = null;

            switch (method) {
                case HTTP_POST_METHOD:
                    ByteArrayEntity entity = new ByteArrayEntity(pdu);
                    // Set request content type.
                    entity.setContentType("application/vnd.wap.mms-message");
                    HttpPost post = new HttpPost(url);
                    post.setEntity(entity);
                    req = post;
                    break;
                case HTTP_GET_METHOD:
                    req = new HttpGet(url);
                    break;
            }

            // Set route parameters for the request.
            HttpParams params = client.getParams();
            if (isProxySet) {
                ConnRouteParams.setDefaultProxy(params, new HttpHost(proxyHost, proxyPort));
            }
            req.setParams(params);

            // Set necessary HTTP headers for MMS transmission.
            req.addHeader(HDR_KEY_ACCEPT, HDR_VALUE_ACCEPT);

            // UA Profile URL header
            String xWapProfileTagName = mmsConfig.getUaProfTagName();
            String xWapProfileUrl = mmsConfig.getUaProfUrl();
            if (xWapProfileUrl != null) {
                Log.v(TAG, "HttpUtils: xWapProfUrl=" + xWapProfileUrl);
                req.addHeader(xWapProfileTagName, xWapProfileUrl);
            }

            // Extra http parameters. Split by '|' to get a list of value pairs.
            // Separate each pair by the first occurrence of ':' to obtain a name and
            // value. Replace the occurrence of the string returned by
            // MmsConfig.getHttpParamsLine1Key() with the users telephone number inside
            // the value. And replace the occurrence of the string returned by
            // MmsConfig.getHttpParamsNaiKey() with the users NAI(Network Access Identifier)
            // inside the value.
            String extraHttpParams = mmsConfig.getHttpParams();

            if (!TextUtils.isEmpty(extraHttpParams)) {
                // Parse the parameter list
                String paramList[] = extraHttpParams.split("\\|");
                for (String paramPair : paramList) {
                    String splitPair[] = paramPair.split(":", 2);
                    if (splitPair.length == 2) {
                        final String name = splitPair[0].trim();
                        final String value = resolveMacro(context, splitPair[1].trim(), mmsConfig);
                        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(value)) {
                            req.addHeader(name, value);
                        }
                    }
                }
            }
            req.addHeader(HDR_KEY_ACCEPT_LANGUAGE, getCurrentAcceptLanguage(Locale.getDefault()));

            final HttpResponse response = client.execute(target, req);
            final StatusLine status = response.getStatusLine();
            final HttpEntity entity = response.getEntity();
            Log.d(TAG, "HttpUtils: status=" + status + " size="
                    + (entity != null ? entity.getContentLength() : -1));
            for (Header header : req.getAllHeaders()) {
                if (header != null) {
                    Log.v(TAG, "HttpUtils: header "
                            + header.getName() + "=" + header.getValue());
                }
            }
            byte[] body = null;
            if (entity != null) {
                try {
                    if (entity.getContentLength() > 0) {
                        body = new byte[(int) entity.getContentLength()];
                        DataInputStream dis = new DataInputStream(entity.getContent());
                        try {
                            dis.readFully(body);
                        } finally {
                            try {
                                dis.close();
                            } catch (IOException e) {
                                Log.e(TAG, "HttpUtils: Error closing input stream: "
                                        + e.getMessage());
                            }
                        }
                    }
                    if (entity.isChunked()) {
                        Log.d(TAG, "HttpUtils: transfer encoding is chunked");
                        int bytesTobeRead = mmsConfig.getMaxMessageSize();
                        byte[] tempBody = new byte[bytesTobeRead];
                        DataInputStream dis = new DataInputStream(entity.getContent());
                        try {
                            int bytesRead = 0;
                            int offset = 0;
                            boolean readError = false;
                            do {
                                try {
                                    bytesRead = dis.read(tempBody, offset, bytesTobeRead);
                                } catch (IOException e) {
                                    readError = true;
                                    Log.e(TAG, "HttpUtils: error reading input stream", e);
                                    break;
                                }
                                if (bytesRead > 0) {
                                    bytesTobeRead -= bytesRead;
                                    offset += bytesRead;
                                }
                            } while (bytesRead >= 0 && bytesTobeRead > 0);
                            if (bytesRead == -1 && offset > 0 && !readError) {
                                // offset is same as total number of bytes read
                                // bytesRead will be -1 if the data was read till the eof
                                body = new byte[offset];
                                System.arraycopy(tempBody, 0, body, 0, offset);
                                Log.d(TAG, "HttpUtils: Chunked response length " + offset);
                            } else {
                                Log.e(TAG, "HttpUtils: Response entity too large or empty");
                            }
                        } finally {
                            try {
                                dis.close();
                            } catch (IOException e) {
                                Log.e(TAG, "HttpUtils: Error closing input stream", e);
                            }
                        }
                    }
                } finally {
                    if (entity != null) {
                        entity.consumeContent();
                    }
                }
            }
            if (status.getStatusCode() != 200) { // HTTP 200 is success.
                StringBuilder sb = new StringBuilder();
                if (body != null) {
                    sb.append("response: text=").append(new String(body)).append('\n');
                }
                for (Header header : req.getAllHeaders()) {
                    if (header != null) {
                        sb.append("req header: ")
                                .append(header.getName())
                                .append('=')
                                .append(header.getValue())
                                .append('\n');
                    }
                }
                for (Header header : response.getAllHeaders()) {
                    if (header != null) {
                        sb.append("resp header: ")
                                .append(header.getName())
                                .append('=')
                                .append(header.getValue())
                                .append('\n');
                    }
                }
                Log.e(TAG, "HttpUtils: error response -- \n"
                        + "mStatusCode=" + status.getStatusCode() + "\n"
                        + "reason=" + status.getReasonPhrase() + "\n"
                        + "url=" + url + "\n"
                        + "method=" + methodString + "\n"
                        + "isProxySet=" + isProxySet + "\n"
                        + "proxyHost=" + proxyHost + "\n"
                        + "proxyPort=" + proxyPort
                        + (sb != null ? "\n" + sb.toString() : ""));
                throw new MmsHttpException(status.getReasonPhrase());
            }
            return body;
        } catch (IOException e) {
            Log.e(TAG, "HttpUtils: IO failure", e);
            throw new MmsHttpException(e);
        } catch (URISyntaxException e) {
            Log.e(TAG, "HttpUtils: invalid url " + url);
            throw new MmsHttpException("Invalid url " + url);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static String getMethodString(int method) {
        return ((method == HTTP_POST_METHOD) ?
                "POST" : ((method == HTTP_GET_METHOD) ? "GET" : "UNKNOWN"));
    }

    /**
     * Create an HTTP client
     *
     * @param context
     * @return {@link android.net.http.AndroidHttpClient}
     */
    private static NetworkAwareHttpClient createHttpClient(Context context, NameResolver resolver,
            boolean useIpv6, MmsConfig.Overridden mmsConfig) {
        final String userAgent = mmsConfig.getUserAgent();
        final NetworkAwareHttpClient client = NetworkAwareHttpClient.newInstance(userAgent, context,
                resolver, useIpv6);
        final HttpParams params = client.getParams();
        HttpProtocolParams.setContentCharset(params, "UTF-8");

        // set the socket timeout
        int soTimeout = mmsConfig.getHttpSocketTimeout();

        Log.v(TAG, "HttpUtils: createHttpClient w/ socket timeout "
                + soTimeout + " ms, UA=" + userAgent);
        HttpConnectionParams.setSoTimeout(params, soTimeout);
        return client;
    }

    private static final String ACCEPT_LANG_FOR_US_LOCALE = "en-US";

    /**
     * Return the Accept-Language header.  Use the current locale plus
     * US if we are in a different locale than US.
     * This code copied from the browser's WebSettings.java
     *
     * @return Current AcceptLanguage String.
     */
    public static String getCurrentAcceptLanguage(Locale locale) {
        final StringBuilder buffer = new StringBuilder();
        addLocaleToHttpAcceptLanguage(buffer, locale);

        if (!Locale.US.equals(locale)) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(ACCEPT_LANG_FOR_US_LOCALE);
        }

        return buffer.toString();
    }

    /**
     * Convert obsolete language codes, including Hebrew/Indonesian/Yiddish,
     * to new standard.
     */
    private static String convertObsoleteLanguageCodeToNew(String langCode) {
        if (langCode == null) {
            return null;
        }
        if ("iw".equals(langCode)) {
            // Hebrew
            return "he";
        } else if ("in".equals(langCode)) {
            // Indonesian
            return "id";
        } else if ("ji".equals(langCode)) {
            // Yiddish
            return "yi";
        }
        return langCode;
    }

    private static void addLocaleToHttpAcceptLanguage(StringBuilder builder, Locale locale) {
        final String language = convertObsoleteLanguageCodeToNew(locale.getLanguage());
        if (language != null) {
            builder.append(language);
            final String country = locale.getCountry();
            if (country != null) {
                builder.append("-");
                builder.append(country);
            }
        }
    }

    private static final Pattern MACRO_P = Pattern.compile("##(\\S+)##");
    /**
     * Resolve the macro in HTTP param value text
     * For example, "something##LINE1##something" is resolved to "something9139531419something"
     *
     * @param value The HTTP param value possibly containing macros
     * @return The HTTP param with macro resolved to real value
     */
    private static String resolveMacro(Context context, String value,
            MmsConfig.Overridden mmsConfig) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        final Matcher matcher = MACRO_P.matcher(value);
        int nextStart = 0;
        StringBuilder replaced = null;
        while (matcher.find()) {
            if (replaced == null) {
                replaced = new StringBuilder();
            }
            final int matchedStart = matcher.start();
            if (matchedStart > nextStart) {
                replaced.append(value.substring(nextStart, matchedStart));
            }
            final String macro = matcher.group(1);
            final String macroValue = mmsConfig.getHttpParamMacro(context, macro);
            if (macroValue != null) {
                replaced.append(macroValue);
            } else {
                Log.w(TAG, "HttpUtils: invalid macro " + macro);
            }
            nextStart = matcher.end();
        }
        if (replaced != null && nextStart < value.length()) {
            replaced.append(value.substring(nextStart));
        }
        return replaced == null ? value : replaced.toString();
    }
}
