package com.klinker.android.send_message;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.mms.util_alt.SqliteWrapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common methods to be used for data connectivity/sending messages ect
 *
 * @author Jake Klinker
 */
public class Utils {
    /**
     * characters to compare against when checking for 160 character sending compatibility
     */
    public static final String GSM_CHARACTERS_REGEX = "^[A-Za-z0-9 \\r\\n@Ł$ĽčéůěňÇŘřĹĺ\u0394_\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039EĆćßÉ!\"#$%&'()*+,\\-./:;<=>?ĄÄÖŃÜ§żäöńüŕ^{}\\\\\\[~\\]|\u20AC]*$";

    /**
     * Gets the current users phone number
     *
     * @param context is the context of the activity or service
     * @return a string of the phone number on the device
     */
    public static String getMyPhoneNumber(Context context) {
        TelephonyManager mTelephonyMgr;
        mTelephonyMgr = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        return mTelephonyMgr.getLine1Number();
    }

    /**
     * Enable mobile connection for a specific address
     *
     * @param address the address to enable
     * @return true for success, else false
     */
    public static void forceMobileConnectionForAddress(ConnectivityManager mConnMgr, String address) {
        //find the host name to route
        String hostName = extractAddressFromUrl(address);
        if (TextUtils.isEmpty(hostName)) hostName = address;

        //create a route for the specified address
        int hostAddress = lookupHost(hostName);
        mConnMgr.requestRouteToHost(ConnectivityManager.TYPE_MOBILE_MMS, hostAddress);
    }

    /**
     * Function for getting the weird auth token used to send or receive google voice messages
     *
     * @param account is the string of the account name to get the auth token for
     * @param context is the context of the activity or service
     * @return a string of the auth token to be saved for later
     * @throws java.io.IOException
     * @throws android.accounts.OperationCanceledException
     * @throws android.accounts.AuthenticatorException
     */
    public static String getAuthToken(String account, Context context) throws IOException, OperationCanceledException, AuthenticatorException {
        Bundle bundle = AccountManager.get(context).getAuthToken(new Account(account, "com.google"), "grandcentral", true, null, null).getResult();
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }

    /**
     * This method extracts from address the hostname
     *
     * @param url eg. http://some.where.com:8080/sync
     * @return some.where.com
     */
    public static String extractAddressFromUrl(String url) {
        String urlToProcess = null;

        //find protocol
        int protocolEndIndex = url.indexOf("://");
        if (protocolEndIndex > 0) {
            urlToProcess = url.substring(protocolEndIndex + 3);
        } else {
            urlToProcess = url;
        }

        // If we have port number in the address we strip everything
        // after the port number
        int pos = urlToProcess.indexOf(':');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have resource location in the address then we strip
        // everything after the '/'
        pos = urlToProcess.indexOf('/');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have ? in the address then we strip
        // everything after the '?'
        pos = urlToProcess.indexOf('?');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }
        return urlToProcess;
    }

    /**
     * Transform host name in int value used by ConnectivityManager.requestRouteToHost
     * method
     *
     * @param hostname
     * @return -1 if the host doesn't exists, elsewhere its translation
     * to an integer
     */
    public static int lookupHost(String hostname) {
        InetAddress inetAddress;

        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }

        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                | (addrBytes[0] & 0xff);

        return addr;
    }

    /**
     * Ensures that the host MMSC is reachable
     *
     * @param context is the context of the activity or service
     * @param url     is the MMSC to check
     * @param proxy   is the proxy of the APN to check
     * @throws java.io.IOException when route cannot be established
     */
    public static void ensureRouteToHost(Context context, String url, String proxy) throws IOException {
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        connMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

        Log.v("sending_mms_library", "ensuring route to host");

        int inetAddr;
        if (proxy != null && !proxy.equals("")) {
            String proxyAddr = proxy;
            inetAddr = lookupHost(proxyAddr);
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.TYPE_MOBILE_MMS, inetAddr)) {
                    throw new IOException("Cannot establish route to proxy " + inetAddr);
                }
            }
        } else {
            Uri uri = Uri.parse(url);
            inetAddr = lookupHost(uri.getHost());
            if (inetAddr == -1) {
                throw new IOException("Cannot establish route for " + url + ": Unknown host");
            } else {
                if (!connMgr.requestRouteToHost(
                        ConnectivityManager.TYPE_MOBILE_MMS, inetAddr)) {
                    throw new IOException("Cannot establish route to " + inetAddr + " for " + url);
                }
            }
        }
    }

    /**
     * Checks whether or not mobile data is enabled and returns the result
     *
     * @param context is the context of the activity or service
     * @return true if data is enabled or false if disabled
     */
    public static Boolean isMobileDataEnabled(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            Class<?> c = Class.forName(cm.getClass().getName());
            Method m = c.getDeclaredMethod("getMobileDataEnabled");
            m.setAccessible(true);
            return (Boolean) m.invoke(cm);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Toggles mobile data
     *
     * @param context is the context of the activity or service
     * @param enabled is whether to enable or disable data
     */
    public static void setMobileDataEnabled(Context context, boolean enabled) {
        try {
            ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Class conmanClass = Class.forName(conman.getClass().getName());
            Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
            iConnectivityManagerField.setAccessible(true);
            Object iConnectivityManager = iConnectivityManagerField.get(conman);
            Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());
            Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);

            setMobileDataEnabledMethod.invoke(iConnectivityManager, enabled);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Gets the number of pages in the SMS based on settings and the length of string
     *
     * @param text     is the text from the message object to be sent
     * @return the number of pages required to hold message
     */
    public static int getNumPages(String text) {
        if (Settings.get().getStripUnicode()) {
            text = StripAccents.stripAccents(text);
        }

        int[] data = SmsMessage.calculateLength(text, false);
        return data[0];
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     * @param context is the context of the activity or service
     * @param recipient is the person message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(Context context, String recipient) {
        Set<String> recipients = new HashSet<String>();

        recipients.add(recipient);
        return getOrCreateThreadId(context, recipients);
    }

    /**
     * Gets the current thread_id or creates a new one for the given recipient
     * @param context is the context of the activity or service
     * @param recipients is the set of people message is being sent to
     * @return the thread_id to use in the database
     */
    public static long getOrCreateThreadId(
            Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (isEmailAddress(recipient)) {
                recipient = extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, new String[]{"_id"}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                } else {

                }
            } finally {
                cursor.close();
            }
        }

        throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    private static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }

        String s = extractAddrSpec(address);
        Matcher match = EMAIL_ADDRESS_PATTERN.matcher(s);
        return match.matches();
    }

    private static final Pattern EMAIL_ADDRESS_PATTERN
            = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    private static String extractAddrSpec(String address) {
        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

        if (match.matches()) {
            return match.group(2);
        }
        return address;
    }

}
