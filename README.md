# Android SMS/MMS/Google Voice Sending Library

These are the APIs that Google has so far left out of the Android echosystem for easily sending any type of message without digging through source code and what not. 

##### This library is still in __BETA__ and has a long way to go... APIs may not be final and things will most likely change.

If you've got a better way to do things, send me a pull request! The library was created specifically for [Sliding Messaging Pro](https://play.google.com/store/apps/details?id=com.klinker.android.messaging_donate&hl=en) and some things work the way they do specifically for that app.

---

## Library Overview

Sending messages is very easy to do.

First, create a settings object with all of your required information for what you want to do. If you don't set something, then it will just be set to a default and that feature may not work. For example, if you need MMS, set the MMSC, proxy, and port, or else you will get an error every time.

``` java
Settings sendSettings = new Settings();

sendSettings.setMmsc("http://mmsc.cingular.com");
sendSettings.setProxy("66.209.11.33");
sendSettings.setPort("80");
sendSettings.setGroup(true);
sendSettings.setPreferVoice(false);
sendSettings.setDeliveryReports(false);
sendSettings.setSplit(false);
sendSettings.setSplitCounter(false);
sendSettings.setStripUnicode(false);
sendSettings.setSignature("");
sendSettings.setSendLongAsMms(true);
sendSettings.setSendLongAsMmsAfter(3);
sendSettings.setAccount("jklinker1@gmail.com");
sendSettings.setRnrSe(null);
```

* MMSC - the URL of your mms provider, found on your phone's APN settings page
* Proxy - more mms information that needs to be set for more providers to send
* Port - again, more mms stuff
* Group - whether you want to send message to multiple senders as an MMS group message or separate SMS/Voice messages
* Prefer Voice - send through Google Voice instead of SMS
* Delivery Reports - request reports for when SMS has been delivered
* Split - splits SMS messages when sent if they are longer than 160 characters
* Split Counter - attaches a split counter to message, ex. (1/3) in front of each message
* Strip Unicode - converts Unicode characters to GSM compatible characters
* Signature - signature to attach at the end of messages
* Send Long as MMS - when a message is a certain length, it is sent as MMS instead of SMS
* Send Long as MMS After - length to convert the long SMS into an MMS
* Account - this is the email address of the account that you want to send google voice messages from
* RnrSe - this is a weird token that google requires to send the message, nullifying it will make the library find the token every time, I'll hit later how to save the token and save your users some data down below in the Google Voice section.

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
```

Be sure to replace the [insert package name here] with your package name defined in the manifest. For example, Sliding Messaging's is com.klinker.android.messaging_donate.

Lastly, you'll need to include permissions in your manifest depending on what you want to do. Here are all of them:

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
<uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
<uses-permission android:name="android.permission.USE_CREDENTIALS" />
<uses-permission android:name="android.permission.GET_ACCOUNTS" />
```

---

## Google Voice Overview

To be able to send Google Voice messages, all you really need to do is add the final 3 permissions above and get the address of the Account that you want to send through.

To get a list of accounts available on the device, you can use the following:

```java
ArrayList<Account> accounts = new ArrayList<Account>();
for (Account account : AccountManager.get(context).getAccountsByType("com.google")) {
	accounts.add(account);
}
```

Display those in a list and let the user choose which one they want to use and save that choice to your SharedPreferences.

Next, when you are configuring your send settings, you should register a receiver that listens for the action "com.klinker.android.send_message.RNRSE" like so:

```java
if (sendSettings.getAccount() != null && sendSettings.getRnrSe() == null) {
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			sharedPrefs.edit().putString("voice_rnrse", intent.getStringExtra("_rnr_se")).commit();
		}
	};

	context.registerReceiver(receiver, new IntentFilter("com.klinker.android.send_message.RNRSE"));
}
```

That code will then save the RnrSe value so that I don't have to fetch it every time and waste time and data. After it is saved, just insert that value into the send settings instead and you are good to go.

---

### Dependencies Information

This library relies on Ion by Koush. That library is packaged into here making it easier to use. Please note that there are also a few jars that are in the ion/libs folder that need to be set as dependencies for both this library and Ion to get it compiling.

---

Don't hesitate to contact me if you have any questions!
Email: jklinker1@gmail.com

---

## License

    Copyright 2013 Jacob Klinker

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.