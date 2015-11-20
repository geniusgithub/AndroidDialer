/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.calllog;

import com.android.dialer.R;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * View holder class for a promo card which will appear in the voicemail tab.
 */
public class PromoCardViewHolder extends RecyclerView.ViewHolder {
    public static PromoCardViewHolder create(View rootView) {
        return new PromoCardViewHolder(rootView);
    }

    /**
     * The "Settings" button view.
     */
    private View mSettingsTextView;

    /**
     * The "Ok" button view.
     */
    private View mOkTextView;

    /**
     * Creates an instance of the {@link ViewHolder}.
     *
     * @param rootView The root view.
     */
    private PromoCardViewHolder(View rootView) {
        super(rootView);

        mSettingsTextView = rootView.findViewById(R.id.settings_action);
        mOkTextView = rootView.findViewById(R.id.ok_action);
    }

    /**
     * Retrieves the "Settings" button.
     *
     * @return The view.
     */
    public View getSettingsTextView() {
        return mSettingsTextView;
    }

    /**
     * Retrieves the "Ok" button.
     *
     * @return The view.
     */
    public View getOkTextView() {
        return mOkTextView;
    }
}
