package com.klinker.android.send_message;

import android.net.wifi.WifiInfo;

public class Settings {

    private String mmsc;
    private String proxy;
    private String port;
    private boolean group;
    private boolean wifiMmsFix;
    private boolean preferVoice;
    private boolean deliveryReports;
    private boolean split;
    private boolean splitCounter;
    private boolean stripUnicode;
    private String signature;
    private boolean sendLongAsMms;
    private int sendLongAsMmsAfter;

    public WifiInfo currentWifi;
    public boolean currentWifiState;
    public DisconnectWifi discon;
    public boolean currentDataState;

    public Settings() {
        this("", "", "", true, true, false, false, false);
    }

    public Settings(String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix) {
        this(mmsc, proxy, port, group, wifiMmsFix, false, false, false);
    }

    public Settings(boolean preferVoice, boolean deliveryReports, boolean split) {
        this("", "", "", true, true, preferVoice, deliveryReports, split);
    }

    public Settings(String mmsc, String proxy, String port, boolean group, boolean wifiMmsFix, boolean preferVoice, boolean deliveryReports, boolean split) {
        this(mmsc, proxy, port, group, wifiMmsFix, preferVoice, deliveryReports, split, false, false, "", true, 3);
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
