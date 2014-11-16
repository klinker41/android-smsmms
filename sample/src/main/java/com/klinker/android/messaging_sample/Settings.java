/*
 * Copyright 2014 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.klinker.android.messaging_sample;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {

    private static Settings settings;

    private static final String MMSC_PREF = "mmsc_url";
    private static final String MMS_PROXY_PREF = "mms_proxy";
    private static final String MMS_PORT_PREF = "mms_port";

    private String mmsc;
    private String mmsProxy;
    private String mmsPort;

    public static Settings get(Context context) {
        return get(context, false);
    }

    public static Settings get(Context context, boolean forceReload) {
        if (settings == null || forceReload) {
            settings = init(context);
        }

        return settings;
    }

    private Settings() {
    }

    private static Settings init(Context context) {
        Settings settings = new Settings();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        settings.mmsc = sharedPreferences.getString(MMSC_PREF, "");
        settings.mmsProxy = sharedPreferences.getString(MMS_PROXY_PREF, "");
        settings.mmsPort = sharedPreferences.getString(MMS_PORT_PREF, "");

        return settings;
    }

    public String getMmsc() {
        return mmsc;
    }

    public String getMmsProxy() {
        return mmsProxy;
    }

    public String getMmsPort() {
        return mmsPort;
    }
}
