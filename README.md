# Android SMS/MMS Sending Library

![Promo](https://raw.githubusercontent.com/klinker41/android-smsmms/master/android-messaging.png)

These are the APIs that Google has so far left out of the Android ecosystem for easily sending any type of message without digging through source code and what not.

##### This library is still in __BETA__ and has a long way to go... APIs may not be final and things will most likely change.

If you've got a better way to do things, send me a pull request! The library was created specifically for [Sliding Messaging Pro](https://play.google.com/store/apps/details?id=com.klinker.android.messaging_donate&hl=en) and [EvolveSMS](https://play.google.com/store/apps/details?id=com.klinker.android.evolve_sms&hl=en) and some things work the way they do specifically for those apps.

---

## Library Overview

Sending messages is very easy to do.

First, create a settings object with all of your required information for what you want to do. If you don't set something, then it will just be set to a default and that feature may not work. For example, if you need MMS, set the MMSC, proxy, and port, or else you will get an error every time.

``` java
Settings sendSettings = new Settings();
```

Next, attach that settings object to the sender

``` java
Transaction sendTransaction = new Transaction(mContext, sendSettings);
```

Now, create the Message you want to send

``` java
Message mMessage = new Message(textToSend, addressToSendTo);
mMessage.setImage(mBitmap);   // not necessary for voice or sms messages
```

And then all you have to do is send the message

``` java
sendTransaction.sendNewMessage(message, threadId)
```

Note: threadId can be nullified, but this sometimes results in a new thread being created instead of the message being added to an existing thread

If you want to send MMS messages, be sure to add this to your manifest:

``` xml
<service android:name="com.android.mms.transaction.TransactionService"/>
```

That's it, you're done sending :)

You'll also need to register a few receivers for when the messages have been sent and for delivery reports to mark them as read... In your manifest, add these lines:

```xml
<receiver android:name="com.klinker.android.send_message.SentReceiver" >
	<intent-filter>
		<action android:name="[insert package name here].SMS_SENT" />
	</intent-filter>
</receiver>

<receiver android:name="com.klinker.android.send_message.DeliveredReceiver" >
	<intent-filter>
                <action android:name="[insert package name here].SMS_DELIVERED" />
	</intent-filter>
</receiver>
<!-- Your custom receiver which is child of com.klinker.android.send_message.MmsSentReceiver -->
<receiver android:name="[insert your custom receiver here. eg. com.example.sms_mms.receivers.MyMmsSentReceiver]" >
        <intent-filter>
	        <action android:name="com.klinker.android.messaging.MMS_SENT" />
        </intent-filter>
</receiver>
```

Be sure to replace the [insert package name here] with your package name defined in the manifest. For example, Sliding Messaging's is com.klinker.android.messaging_donate.

Lastly, you'll need to include permissions in your manifest depending on what you want to do. Here are all of them (there are a lot unfortunately):

```xml
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.WRITE_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_MMS"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.provider.Telephony.SMS_RECEIVED" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```

For full details on how to implement, please check out the sample application. It has more information on permissions, creating a transaction and sending a message.

---

### Dependencies Information

To include in your gradle project:

```groovy
compile 'com.klinkerapps:android-smsmms:3.4.5'
```

---

## License

    Copyright 2014 Jacob Klinker

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
