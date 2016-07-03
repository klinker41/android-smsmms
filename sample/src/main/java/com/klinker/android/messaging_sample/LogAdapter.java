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

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

    private List<String> mDataset;
    private Handler mHandler;

    public LogAdapter(List<String> myDataset) {
        mDataset = myDataset;
        mHandler = new Handler();
    }

    @Override
    public LogAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final Context context = parent.getContext();
        TextView v = (TextView) LayoutInflater.from(context)
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        final ViewHolder vh = new ViewHolder(v);

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String dataClicked = mDataset.get(vh.position);

                new AlertDialog.Builder(context)
                        .setMessage(dataClicked)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mTextView.setText(mDataset.get(position));
        holder.position = position;
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void addItem(String item) {
        mDataset.add(0, item);

        // ensure we are on the ui thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    notifyItemInserted(0);
                } catch (IllegalStateException e) {
                    // Cannot call this method while recyclerview is computing a layout or scrolling
                }
            }
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
        public int position;

        public ViewHolder(TextView v) {
            super(v);
            mTextView = v;
            mTextView.setTextSize(10);
        }
    }

}
