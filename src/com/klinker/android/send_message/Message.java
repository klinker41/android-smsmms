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

import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class Message {

    private String text;
    private String[] addresses;
    private Bitmap[] images;

    public Message() {
        this("", new String[] {""});
    }

    public Message(String text, String address) {
        this(text, address.trim().split(" "));
    }

    public Message(String text, String[] addresses) {
        this.text = text;
        this.addresses = addresses;
        this.images = new Bitmap[0];
    }

    public Message(String text, String address, Bitmap image) {
        this(text, address.trim().split(" "), new Bitmap[] {image});
    }

    public Message(String text, String[] addresses, Bitmap image) {
        this(text, addresses, new Bitmap[] {image});
    }

    public Message(String text, String address, Bitmap[] images) {
        this(text, address.trim().split(" "), images);
    }

    public Message(String text, String[] addresses, Bitmap[] images) {
        this.text = text;
        this.addresses = addresses;
        this.images = images;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setAddresses(String[] addresses) {
        this.addresses = addresses;
    }

    public void setAddress(String address) {
        this.addresses = new String[1];
        this.addresses[0] = address;
    }

    public void setImages(Bitmap[] images) {
        this.images = images;
    }

    public void setImage(Bitmap image) {
        this.images = new Bitmap[1];
        this.images[0] = image;
    }

    public void addAddress(String address) {
        String[] temp = this.addresses;

        if (temp == null) {
            temp = new String[0];
        }

        this.addresses = new String[temp.length + 1];

        for (int i = 0; i < temp.length; i++) {
            this.addresses[i] = temp[i];
        }

        this.addresses[temp.length] = address;
    }

    public void addImage(Bitmap image) {
        Bitmap[] temp = this.images;

        if (temp == null) {
            temp = new Bitmap[0];
        }

        this.images = new Bitmap[temp.length + 1];

        for (int i = 0; i < temp.length; i++) {
            this.images[i] = temp[i];
        }

        this.images[temp.length] = image;
    }

    public String getText() {
        return this.text;
    }

    public String[] getAddresses() {
        for (int i = 0; i < addresses.length; i++) {
            Log.v("addresses", i + ": " + addresses[i]);
        }

        return this.addresses;
    }

    public Bitmap[] getImages() {
        return this.images;
    }

    public static byte[] bitmapToByteArray(Bitmap image) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
}
