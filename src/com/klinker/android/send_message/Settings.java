/*
 * Copyright 2013 Jacob Klinker
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
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

public class Settings {

    // MMS options
    private String mmsc;
    private String proxy;
    private String port;
    private boolean group;
    private boolean wifiMmsFix;

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

    // used for WiFi workaround when sending MMS to hold previous state data before disabling
    public WifiInfo currentWifi;
    public boolean currentWifiState;
    public DisconnectWifi discon;
    public boolean currentDataState;

    public Settings() {
        this("", "", "", true, true, false, false, false, false, false, "", true, 3);
    }

    public Settings(String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix, boolean preferVoice, boolean deliveryReports, boolean split, boolean splitCounter, boolean stripUnicode, String signature, boolean sendLongAsMms, int sendLongAsMmsAfter) {
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
    }

    public void setMmsc(String mmsc) {
        this.mmsc = mmsc;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public void setWifiMmsFix(boolean wifiMmsFix) {
        this.wifiMmsFix = wifiMmsFix;
    }

    public void setPreferVoice(boolean preferVoice) {
        this.preferVoice = preferVoice;
    }

    public void setDeliveryReports(boolean deliveryReports) {
        this.deliveryReports = deliveryReports;
    }

    public void setSplit(boolean split) {
        this.split = split;
    }

    public void setSplitCounter(boolean splitCounter) {
        this.splitCounter = splitCounter;
    }

    public void setStripUnicode(boolean stripUnicode) {
        this.stripUnicode = stripUnicode;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setSendLongAsMms(boolean sendLongAsMms) {
        this.sendLongAsMms = sendLongAsMms;
    }

    public void setSendLongAsMmsAfter(int sendLongAsMmsAfter) {
        this.sendLongAsMmsAfter = sendLongAsMmsAfter;
    }

    public String getMmsc() {
        return this.mmsc;
    }

    public String getProxy() {
        return this.proxy;
    }

    public String getPort() {
        return this.port;
    }

    public boolean getGroup() {
        return this.group;
    }

    public boolean getWifiMmsFix() {
        return this.wifiMmsFix;
    }

    public boolean getPreferVoice() {
        return this.preferVoice;
    }

    public boolean getDeliveryReports() {
        return this.deliveryReports;
    }

    public boolean getSplit() {
        return this.split;
    }

    public boolean getSplitCounter() {
        return this.splitCounter;
    }

    public boolean getStripUnicode() {
        return this.stripUnicode;
    }

    public String getSignature() {
        return this.signature;
    }

    public boolean getSendLongAsMms() {
        return this.sendLongAsMms;
    }

    public int getSendLongAsMmsAfter() {
        return this.sendLongAsMmsAfter;
    }
}
