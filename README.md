# Android SMS/MMS/Google Voice Sending Library

These are the APIs that Google has so far left out of the Android echosystem for easily sending any type of message without digging through source code and what not. 

##### This library is still in __BETA__ and has a long way to go... APIs may not be final and things will most likely change.

If you've got a better way to do things, send me a pull request! The library was created specifically for [Sliding Messaging Pro] (https://play.google.com/store/apps/details?id=com.klinker.android.messaging_donate&hl=en) and some things work the way they do specifically for that app.

---

## Library Overview

Sending messages is very easy to do.

First, create a settings object with all of your required information for what you want to do. If you don't set something, then it will just be set to a default and that feature may not work. For example, if you need MMS, set the MMSC, proxy, and port, or else you will get an error every time.

``` java
Settings sendSettings = new Settings();

sendSettings.setMmsc(http://mmsc.cingular.com);
sendSettings.setProxy(66.209.11.33);
sendSettings.setPort(80);
sendSettings.setGroup(true);
sendSettings.setWifiMmsFix(true);
sendSettings.setPreferVoice(false);
sendSettings.setDeliveryReports(false);
sendSettings.setSplit(false);
sendSettings.setSplitCounter(false);
sendSettings.setStripUnicode(false);
sendSettings.setSignature("");
sendSettings.setSendLongAsMms(true);
sendSettings.setSendLongAsMmsAfter(3);
```

* MMSC - the URL of your mms provider, found on your phone's APN settings page
* Proxy - more mms information that needs to be set for more providers to send
* Port - again, more mms stuff
* Group - whether you want to send message to multiple senders as an MMS group message or separate SMS/Voice messages
* WiFi MMS Fix - will disable wifi to send the message, only way I've found to do it, so if you can find the problem, submit a pull request :)
* Prefer Voice - send through Google Voice instead of SMS
* Delivery Reports - request reports for when SMS has been delivered
* Split - splits SMS messages when sent if they are longer than 160 characters
* Split Counter - attaches a split counter to message, ex. (1/3) in front of each message
* Strip Unicode - converts Unicode characters to GSM compatible characters
* Signature - signature to attach at the end of messages
* Send Long as MMS - when a message is a certain length, it is sent as MMS instead of SMS
* Send Long as MMS After - length to convert the long SMS into an MMS

Next, attach that settings object to the sender

``` java
Transaction sendTransaction = new Transaction(mContext, sendSettings);
```

Now, create the Message you want to send

``` java
Message mMessage = new Message(textToSend, addressToSendTo);
mMessage.setImage(mBitmap);
```

And then all you have to do is send the message

``` java
sendTransaction.sendNewMessage(message, threadId)
```

Note: threadId can be nullified, but this sometimes results in a new thread being created instead of the message being added to an existing thread

That's it, you're done :)

--

## License

    Copyright 2013 Chris Banes

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.