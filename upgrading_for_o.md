## Upgrading for Android O

Several changes in Android O affect the way that this library functions, specifically the broadcasts
sent to let you know when an SMS/MMS has been sent or delivered. When upgrading to version 4.0.0+
of the library from 3.x.x, several changes are required for functionality to work correctly.

Specifically, each `receiver` that you have registered in your `AndroidManifest.xml` that is from
this library needs to be modified. You'll need to remove the `intent-filter` tag and instead add
a `taskAffinity` item.

The old entry looks like this:

```java
<receiver
    android:name=".MmsSentReceiver">
    <intent-filter>
        <action android:name="com.klinker.android.messaging.MMS_SENT" />
    </intent-filter>
</receiver>
```

The changes should look like this:

```java
<receiver
    android:name=".MmsSentReceiver"
    android:taskAffinity="com.klinker.android.messaging.MMS_SENT"/>
```

For a full example, here are the receivers from one of my own apps:

```java
<receiver
    android:name=".receiver.SmsReceivedReceiver"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_DELIVER" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.SmsSentReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="${applicationId}.SMS_SENT" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.SmsDeliveredReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="${applicationId}.SMS_DELIVERED" />
    </intent-filter>
</receiver>

<receiver
    android:name="com.android.mms.transaction.PushReceiver"
    android:permission="android.permission.BROADCAST_WAP_PUSH">
    <intent-filter>
        <action android:name="android.provider.Telephony.WAP_PUSH_DELIVER" />
        <data android:mimeType="application/vnd.wap.mms-message" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.MmsSentReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.klinker.android.messaging.MMS_SENT" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.MmsReceivedReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.klinker.android.messaging.MMS_RECEIVED" />
    </intent-filter>
</receiver>
```

After the changes, this gets reduced to the following:

```java
<receiver
    android:name=".receiver.SmsReceivedReceiver"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_DELIVER" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.SmsSentReceiver"
    android:taskAffinity="${applicationId}.SMS_SENT"/>

<receiver
    android:name=".receiver.SmsDeliveredReceiver"
    android:taskAffinity="${applicationId}.SMS_DELIVERED"/>

<receiver
    android:name="com.android.mms.transaction.PushReceiver"
    android:permission="android.permission.BROADCAST_WAP_PUSH">
    <intent-filter>
        <action android:name="android.provider.Telephony.WAP_PUSH_DELIVER" />
        <data android:mimeType="application/vnd.wap.mms-message" />
    </intent-filter>
</receiver>

<receiver
    android:name=".receiver.MmsSentReceiver"
    android:taskAffinity="com.klinker.android.messaging.MMS_SENT"/>

<receiver
    android:name=".receiver.MmsReceivedReceiver"
    android:taskAffinity="com.klinker.android.messaging.MMS_RECEIVED"/>
```
