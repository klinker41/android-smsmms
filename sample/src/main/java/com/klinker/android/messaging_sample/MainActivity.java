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

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.klinker.android.logger.Log;
import com.klinker.android.logger.OnLogListener;
import com.klinker.android.send_message.ApnUtils;
import com.klinker.android.send_message.BroadcastUtils;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private Settings settings;

    private Button setDefaultAppButton;
    private Button selectApns;
    private EditText fromField;
    private EditText toField;
    private EditText messageField;
    private ImageView imageToSend;
    private Button sendButton;
    private RecyclerView log;

    private LogAdapter logAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("request_permissions", true) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initSettings();
        initViews();
        initActions();
        initLogging();

        BroadcastUtils.sendExplicitBroadcast(this, new Intent(), "test action");
    }

    private void initSettings() {
        settings = Settings.get(this);

        if (TextUtils.isEmpty(settings.getMmsc()) &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            initApns();
        }
    }

    private void initApns() {
        ApnUtils.initDefaultApns(this, new ApnUtils.OnApnFinishedListener() {
            @Override
            public void onFinished() {
                settings = Settings.get(MainActivity.this, true);
            }
        });
    }

    private void initViews() {
        setDefaultAppButton = (Button) findViewById(R.id.set_as_default);
        selectApns = (Button) findViewById(R.id.apns);
        fromField = (EditText) findViewById(R.id.from);
        toField = (EditText) findViewById(R.id.to);
        messageField = (EditText) findViewById(R.id.message);
        imageToSend = (ImageView) findViewById(R.id.image);
        sendButton = (Button) findViewById(R.id.send);
        log = (RecyclerView) findViewById(R.id.log);
    }

    private void initActions() {
        if (Utils.isDefaultSmsApp(this)) {
            setDefaultAppButton.setVisibility(View.GONE);
        } else {
            setDefaultAppButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setDefaultSmsApp();
                }
            });
        }

        selectApns.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initApns();
            }
        });

        fromField.setText(Utils.getMyPhoneNumber(this));
        toField.setText(Utils.getMyPhoneNumber(this));

        imageToSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSendImage();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        log.setHasFixedSize(false);
        log.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter(new ArrayList<String>());
        log.setAdapter(logAdapter);
    }

    private void initLogging() {
        Log.setDebug(true);
        Log.setPath("messenger_log.txt");
        Log.setLogListener(new OnLogListener() {
            @Override
            public void onLogged(String tag, String message) {
                //logAdapter.addItem(tag + ": " + message);
            }
        });
    }

    private void setDefaultSmsApp() {
        setDefaultAppButton.setVisibility(View.GONE);
        Intent intent =
                new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                getPackageName());
        startActivity(intent);
    }

    private void toggleSendImage() {
        if (imageToSend.isEnabled()) {
            imageToSend.setEnabled(false);
            imageToSend.setAlpha(0.3f);
        } else {
            imageToSend.setEnabled(true);
            imageToSend.setAlpha(1.0f);
        }
    }

    public void sendMessage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                com.klinker.android.send_message.Settings sendSettings = new com.klinker.android.send_message.Settings();
                sendSettings.setMmsc(settings.getMmsc());
                sendSettings.setProxy(settings.getMmsProxy());
                sendSettings.setPort(settings.getMmsPort());
                sendSettings.setUseSystemSending(true);

                Transaction transaction = new Transaction(MainActivity.this, sendSettings);

                Message message = new Message(messageField.getText().toString(), toField.getText().toString());

                if (imageToSend.isEnabled()) {
                    message.setImage(BitmapFactory.decodeResource(getResources(), R.drawable.android));
                }

                transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
            }
        }).start();
    }

}
