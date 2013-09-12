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

import android.net.wifi.WifiInfo;

/**
 * Class to house all of the settings that can be used to send a message
 * @author Jake Klinker
 */
public class Settings {

    // MMS options
    private String mmsc;
    private String proxy;
    private String port;
    private boolean group;

    // Google Voice options
    private boolean preferVoice;

    // SMS options
    private boolean deliveryReports;
    private boolean split;
    private boolean splitCounter;
    private boolean stripUnicode;
    private String signature;
    private boolean sendLongAsMms;
    private int sendLongAsMmsAfter;

    // Google Voice settings
    private String account;
    private String rnrSe;

    /**
     * Default constructor to set everything to default values
     */
    public Settings() {
        this("", "", "0", true, true, false, false, false, false, false, "", true, 3, "", null);
    }

    /**
     * Construtor to create object of all values
     * @param mmsc is the address contained by the apn to send MMS to
     * @param proxy is the proxy address in the apn to send MMS through
     * @param port is the port from the apn to send MMS through
     * @param group is a boolean specifying whether or not to send messages with multiple recipients as a group MMS message
     * @param wifiMmsFix is a boolean to toggle on and off wifi when sending MMS (MMS will not work currently when WiFi is enabled)
     * @param preferVoice is a boolean to say whether you want to send through Google Voice or SMS
     * @param deliveryReports is a boolean to retrieve delivery reports from SMS messages
     * @param split is a boolean to manually split messages (shouldn't be necessary, but some carriers do not split on their own)
     * @param splitCounter adds a split counter to the front of all split messages
     * @param stripUnicode replaces many unicode characters with their gsm compatible equivalent to allow for sending 160 characters instead of 70
     * @param signature a signature to attach at the end of each message
     * @param sendLongAsMms if a message is too long to be multiple SMS, convert it to a single MMS
     * @param sendLongAsMmsAfter is an int of how many pages long an SMS must be before it is split
     * @param account is the google account to send Google Voice messages through
     * @param rnrSe is the token to use to send Google Voice messages (nullify if you don't know what this is)
     */
    public Settings(String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix, boolean preferVoice, boolean deliveryReports, boolean split, boolean splitCounter, boolean stripUnicode, String signature, boolean sendLongAsMms, int sendLongAsMmsAfter, String account, String rnrSe) {
        this.mmsc = mmsc;
        this.proxy = proxy;
        this.port = port;
        this.group = group;
        this.wifiMmsFix = wifiMmsFix;
        this.preferVoice = preferVoice;
        this.deliveryReports = deliveryReports;
        this.split = split;
        this.splitCounter = splitCounter;
        this.stripUnicode = stripUnicode;
        this.signature = signature;
        this.sendLongAsMms = sendLongAsMms;
        this.sendLongAsMmsAfter = sendLongAsMmsAfter;
        this.account = account;
        this.rnrSe = rnrSe;
    }

    /**
     * Sets MMSC
     * @param mmsc is the mmsc from the apns
     */
    public void setMmsc(String mmsc) {
        this.mmsc = mmsc;
    }

    /**
     * Sets the MMS Proxy
     * @param proxy is the proxy from the apns
     */
    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    /**
     * Sets the Port
     * @param port is the port from the apns
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Sets group MMS messages
     * @param group is a boolean specifying whether or not to send messages with multiple recipients as a group MMS message
     */
    public void setGroup(boolean group) {
        this.group = group;
    }

    /**
     * Sets wifi mms fix
     * @param wifiMmsFix is a boolean to toggle on and off wifi when sending MMS
     */
    public void setWifiMmsFix(boolean wifiMmsFix) {
        this.wifiMmsFix = wifiMmsFix;
    }

    /**
     * Sets whether to use Voice or SMS for non MMS messages
     * @param preferVoice is a boolean to say whether you want to send through Google Voice or SMS
     */
    public void setPreferVoice(boolean preferVoice) {
        this.preferVoice = preferVoice;
    }

    /**
     * Sets whether to receive delivery reports from SMS messages
     * @param deliveryReports is a boolean to retrieve delivery reports from SMS messages
     */
    public void setDeliveryReports(boolean deliveryReports) {
        this.deliveryReports = deliveryReports;
    }

    /**
     * Sets whether to manually split an SMS or not
     * @param split is a boolean to manually split messages (shouldn't be necessary, but some carriers do not split on their own)
     */
    public void setSplit(boolean split) {
        this.split = split;
    }

    /**
     * Adds a split counter to the front of each split SMS
     * @param splitCounter adds a split counter to the front of all split messages
     */
    public void setSplitCounter(boolean splitCounter) {
        this.splitCounter = splitCounter;
    }

    /**
     * Sets whether or not unicode characters should be sent or converted to their GSM compatible alternative
     * @param stripUnicode replaces many unicode characters with their gsm compatible equivalent to allow for sending 160 characters instead of 70
     */
    public void setStripUnicode(boolean stripUnicode) {
        this.stripUnicode = stripUnicode;
    }

    /**
     * Sets a signature to be attached to each message
     * @param signature a signature to attach at the end of each message
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Sets whether long SMS or Voice messages should instead be sent by a single MMS
     * @param sendLongAsMms if a message is too long to be multiple SMS, convert it to a single MMS
     */
    public void setSendLongAsMms(boolean sendLongAsMms) {
        this.sendLongAsMms = sendLongAsMms;
    }

    /**
     * Sets when we should convert SMS or Voice into an MMS message
     * @param sendLongAsMmsAfter is an int of how many pages long an SMS must be before it is split
     */
    public void setSendLongAsMmsAfter(int sendLongAsMmsAfter) {
        this.sendLongAsMmsAfter = sendLongAsMmsAfter;
    }

    /**
     * Sets the Google account to send Voice messages through
     * @param account is the google account to send Google Voice messages through
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * Sets the token to use to authenticate voice messages
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
     * @return whether or not to send Group MMS or multiple SMS/Voice messages
     */
    public boolean getGroup() {
        return this.group;
    }

    /**
     * @return whether or not to toggle wifi when sending MMS
     */
    public boolean getWifiMmsFix() {
        return this.wifiMmsFix;
    }

    /**
     * @return whether or not to send SMS or Voice messages
     */
    public boolean getPreferVoice() {
        return this.preferVoice;
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
}
