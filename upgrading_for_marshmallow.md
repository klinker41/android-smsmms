## Upgrading your app for Marshmallow + MMS

A lot of changes went into Marshmallow, from the permission system to how data can be transported,
that will affect your app that uses this library for MMS sending (SMS is unaffected).

First and foremost, Apache HTTP utilities have been deprecated and Google now suggests a full move
over to using URLConnection. This required a MAJOR change for this library. All changes have been
applied and you should now see faster sending and receiving times and more stability.

These changes also paved the way for adding support for dual sim devices and this will be coming
to the library in the future since Google added support in Android 5.1.

The permission system has changed drastically in Marshmallow as well. Previously, this library
was able to use the permission CHANGE_NETWORK_STATE to request sending MMS through mobile data
instead of WiFi. This is a requirement for most carriers. This permission now has a SIGNATURE
level permission, meaning that your app that requests it will not be granted it. To get around
this, we need to instead use the WRITE_SETTINGS permission. You can see an example of this in the
included sample application. Basically, you'll need to make the following changes:

1) include <uses-permission> tag in your manifest:

```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS"/>
```

2) perform a check at runtime for whether or not you have been granted this permission for users
running Marshmallow or higher. If it has not been granted, then launch a UI to allow it:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
    new AlertDialog.Builder(this)
            .setMessage(com.klinker.android.send_message.R.string.write_settings_permission)
            .setPositiveButton(com.klinker.android.send_message.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e("MainActivity", "error starting permission intent", e);
                    }
                }
            })
            .show();
    return;
}
```

Making these changes should allow your app to be updated to Marshmallow using compileSdkVersion 23
without too many issues for sending MMS.