/*
 * Copyright 2013 Jacob Klinker
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

package com.klinker.android.send_message;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.preference.PreferenceManager;

/**
 * Class to house all of the settings that can be used to send a message
 *
 * @author Jake Klinker, Fabrice Darbas
 */
public class Settings implements ISettingsConst {

    // MMS options
    private String mmsc;
    private String proxy;
    private String port;
    private String userAgent;
    private String uaProfUrl;
    private String uaProfTagName;
    private boolean group;

    // SMS options
    private boolean deliveryReports;
    private boolean split;
    private boolean splitCounter;
    private boolean stripUnicode;
    private String signature;
    private String preText;
    private boolean sendLongAsMms;
    private int sendLongAsMmsAfter;

    // Google Voice settings
    private String account;
    private String rnrSe;

    private static Settings instance = new Settings();

    public static Settings get() {
        return instance;
    }

    /**
     * Default constructor to set everything to default values
     */
    private Settings() {
        // singleton constructor
        loadDefaultValues();
    }

    /**
     * Loads settings from preferences
     */
    public void loadDefaultValues() {
        setMmsc(DEFAULT_MMSC_URL);
        setProxy(DEFAULT_MMS_PROXY);
        setPort(DEFAULT_MMS_PORT);
        setAgent(DEFAULT_MMS_AGENT);
        setUserProfileUrl(DEFAULT_MMS_USER_AGENT_PROFILE_URL);
        setUaProfTagName(DEFAULT_MMS_USER_AGENT_TAG_NAME);
        setGroup(DEFAULT_GROUP_MESSAGE);
        setDeliveryReports(DEFAULT_DELIVERY_REPORTS);
        setSplit(DEFAULT_SPLIT_SMS);
        setSplitCounter(DEFAULT_SPLIT_COUNTER);
        setStripUnicode(DEFAULT_STRIP_UNICODE);
        setSignature(DEFAULT_SIGNATURE);
        setSendLongAsMms(true);
        setSendLongAsMmsAfter(3);
        setAccount(null);
        setRnrSe(null);
    }

    /**
     * Write settings to preferences
     */
    public void loadFromPreferences(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        setMmsc(sharedPrefs.getString(KEY_MMSC_URL, DEFAULT_MMSC_URL));
        setProxy(sharedPrefs.getString(KEY_MMS_PROXY, DEFAULT_MMS_PROXY));
        setPort(sharedPrefs.getString(KEY_MMS_PORT, DEFAULT_MMS_PORT));
        setAgent(sharedPrefs.getString(KEY_MMS_AGENT, DEFAULT_MMS_AGENT));
        setUserProfileUrl(sharedPrefs.getString(KEY_MMS_USER_AGENT_PROFILE_URL, DEFAULT_MMS_USER_AGENT_PROFILE_URL));
        setUaProfTagName(sharedPrefs.getString(KEY_MMS_USER_AGENT_TAG_NAME, DEFAULT_MMS_USER_AGENT_TAG_NAME));
        setGroup(sharedPrefs.getBoolean(KEY_GROUP_MESSAGE, DEFAULT_GROUP_MESSAGE));
        setDeliveryReports(sharedPrefs.getBoolean(KEY_DELIVERY_REPORTS, DEFAULT_DELIVERY_REPORTS));
        setSplit(sharedPrefs.getBoolean(KEY_SPLIT_SMS, DEFAULT_SPLIT_SMS));
        setSplitCounter(sharedPrefs.getBoolean(KEY_SPLIT_COUNTER, DEFAULT_SPLIT_COUNTER));
        setStripUnicode(sharedPrefs.getBoolean(KEY_STRIP_UNICODE, DEFAULT_STRIP_UNICODE));
        setSignature(sharedPrefs.getString(KEY_SIGNATURE, DEFAULT_SIGNATURE));
        setSendLongAsMms(sharedPrefs.getBoolean(KEY_SEND_LONG_AS_MMS, DEFAULT_SEND_LONG_AS_MMS));
        setSendLongAsMmsAfter(sharedPrefs.getInt(KEY_SEND_LONG_AS_MMS_AFTER, DEFAULT_SEND_LONG_AS_MMS_AFTER));
        setAccount(sharedPrefs.getString(KEY_ACCOUNT, DEFAULT_ACCOUNT));
        setRnrSe(sharedPrefs.getString(KEY_RNRSE, DEFAULT_RNRSE));
    }

    public void writeToPreferences(Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(KEY_MMSC_URL, getMmsc());
        editor.putString(KEY_MMS_PROXY, getProxy());
        editor.putString(KEY_MMS_PORT, getPort());
        editor.putString(KEY_MMS_AGENT, getAgent());
        editor.putString(KEY_MMS_USER_AGENT_PROFILE_URL, getUserProfileUrl());
        editor.putString(KEY_MMS_USER_AGENT_TAG_NAME, getUserProfileUrl());
        editor.putBoolean(KEY_GROUP_MESSAGE, getGroup());
        editor.putBoolean(KEY_DELIVERY_REPORTS, getDeliveryReports());
        editor.putBoolean(KEY_SPLIT_SMS, getSplit());
        editor.putBoolean(KEY_SPLIT_COUNTER, getSplitCounter());
        editor.putBoolean(KEY_STRIP_UNICODE, getStripUnicode());
        editor.putString(KEY_SIGNATURE, getSignature());
        editor.putBoolean(KEY_SEND_LONG_AS_MMS, getSendLongAsMms());
        editor.putInt(KEY_SEND_LONG_AS_MMS_AFTER, getSendLongAsMmsAfter());
        editor.putString(KEY_ACCOUNT, getAccount());
        editor.putString(KEY_RNRSE, getRnrSe());
        editor.commit();
    }

    /**
     * Sets MMSC
     *
     * @param mmsc is the mmsc from the apns
     */
    public void setMmsc(String mmsc) {
        this.mmsc = mmsc;
    }

    /**
     * Sets the MMS Proxy
     *
     * @param proxy is the proxy from the apns
     */
    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    /**
     * Sets the Port
     *
     * @param port is the port from the apns
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Sets the user agent
     *
     * @param agent is the agent to send http request with
     */
    public void setAgent(String agent) {
        this.userAgent = agent;
    }

    /**
     * Sets the user agent profile url
     *
     * @param userProfileUrl is the user agent profile url
     */
    public void setUserProfileUrl(String userProfileUrl) {
        this.uaProfUrl = userProfileUrl;
    }

    /**
     * Sets the user agent profile tag name
     *
     * @param tagName the tag name to use
     */
    public void setUaProfTagName(String tagName) {
        this.uaProfTagName = tagName;
    }

    /**
     * Sets group MMS messages
     *
     * @param group is a boolean specifying whether or not to send messages with multiple recipients as a group MMS message
     */
    public void setGroup(boolean group) {
        this.group = group;
    }

    /**
     * Sets whether to receive delivery reports from SMS messages
     *
     * @param deliveryReports is a boolean to retrieve delivery reports from SMS messages
     */
    public void setDeliveryReports(boolean deliveryReports) {
        this.deliveryReports = deliveryReports;
    }

    /**
     * Sets whether to manually split an SMS or not
     *
     * @param split is a boolean to manually split messages (shouldn't be necessary, but some carriers do not split on their own)
     */
    public void setSplit(boolean split) {
        this.split = split;
    }

    /**
     * Adds a split counter to the front of each split SMS
     *
     * @param splitCounter adds a split counter to the front of all split messages
     */
    public void setSplitCounter(boolean splitCounter) {
        this.splitCounter = splitCounter;
    }

    /**
     * Sets whether or not unicode characters should be sent or converted to their GSM compatible alternative
     *
     * @param stripUnicode replaces many unicode characters with their gsm compatible equivalent to allow for sending 160 characters instead of 70
     */
    public void setStripUnicode(boolean stripUnicode) {
        this.stripUnicode = stripUnicode;
    }

    /**
     * Sets a signature to be attached to each message
     *
     * @param signature a signature to attach at the end of each message
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Sets the text to be sent before an SMS message
     *
     * @param preText text to be attached to the beginning of each message
     */
    public void setPreText(String preText) {
        this.preText = preText;
    }

    /**
     * Sets whether long SMS or Voice messages should instead be sent by a single MMS
     *
     * @param sendLongAsMms if a message is too long to be multiple SMS, convert it to a single MMS
     */
    public void setSendLongAsMms(boolean sendLongAsMms) {
        this.sendLongAsMms = sendLongAsMms;
    }

    /**
     * Sets when we should convert SMS or Voice into an MMS message
     *
     * @param sendLongAsMmsAfter is an int of how many pages long an SMS must be before it is split
     */
    public void setSendLongAsMmsAfter(int sendLongAsMmsAfter) {
        this.sendLongAsMmsAfter = sendLongAsMmsAfter;
    }

    /**
     * Sets the Google account to send Voice messages through
     *
     * @param account is the google account to send Google Voice messages through
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * Sets the token to use to authenticate voice messages
     *
     * @param rnrSe is the token to use to send Google Voice messages (nullify if you don't know what this is)
     */
    public void setRnrSe(String rnrSe) {
        this.rnrSe = rnrSe;
    }

    /**
     * @return MMSC to send through
     */
    public String getMmsc() {
        return this.mmsc;
    }

    /**
     * @return the proxy to send MMS through
     */
    public String getProxy() {
        return this.proxy;
    }

    /**
     * @return the port to send MMS through
     */
    public String getPort() {
        return this.port;
    }

    /**
     * @return the user agent to send mms with
     */
    public String getAgent() {
        return this.userAgent;
    }

    /**
     * @return the user agent profile url to send mms with
     */
    public String getUserProfileUrl() {
        return this.uaProfUrl;
    }

    /**
     * @return the user agent profile tag name
     */
    public String getUaProfTagName() {
        return this.uaProfTagName;
    }

    /**
     * @return whether or not to send Group MMS or multiple SMS/Voice messages
     */
    public boolean getGroup() {
        return this.group;
    }

    /**
     * @return whether or not to request delivery reports on SMS messages
     */
    public boolean getDeliveryReports() {
        return this.deliveryReports;
    }

    /**
     * @return whether or not SMS should be split manually
     */
    public boolean getSplit() {
        return this.split;
    }

    /**
     * @return whether or not a split counter should be attached to manually split messages
     */
    public boolean getSplitCounter() {
        return this.splitCounter;
    }

    /**
     * @return whether or not unicode chars should be substituted with gms characters
     */
    public boolean getStripUnicode() {
        return this.stripUnicode;
    }

    /**
     * @return the signature attached to SMS messages
     */
    public String getSignature() {
        return this.signature;
    }

    /**
     * @return the text attached to the beginning of each SMS
     */
    public String getPreText() {
        return this.preText;
    }

    /**
     * @return whether or not to send long SMS or Voice as single MMS
     */
    public boolean getSendLongAsMms() {
        return this.sendLongAsMms;
    }

    /**
     * @return number of pages sms must be to send instead as MMS
     */
    public int getSendLongAsMmsAfter() {
        return this.sendLongAsMmsAfter;
    }

    /**
     * @return Google account to send Voice messages through
     */
    public String getAccount() {
        return this.account;
    }

    /**
     * @return auth token to be used for Voice messages
     */
    public String getRnrSe() {
        return this.rnrSe;
    }

    /**
     * @deprecated
     */
    private boolean wifiMmsFix;

    /**
     * @deprecated
     */
    public WifiInfo currentWifi;

    /**
     * @deprecated
     */
    public boolean currentWifiState;

    /**
     * @deprecated
     */
    public DisconnectWifi discon;

    /**
     * @deprecated
     */
    public boolean currentDataState;

    /**
     * @param wifiMmsFix is a boolean to toggle on and off wifi when sending MMS
     * @deprecated Sets wifi mms fix
     */
    public void setWifiMmsFix(boolean wifiMmsFix) {
        this.wifiMmsFix = wifiMmsFix;
    }

    /**
     * @return whether or not to toggle wifi when sending MMS
     * @deprecated
     */
    public boolean getWifiMmsFix() {
        return this.wifiMmsFix;
    }
}
