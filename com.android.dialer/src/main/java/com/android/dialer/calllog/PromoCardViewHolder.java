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

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.R;

/**
 * Generic ViewHolder class for a promo card with a primary and secondary action.
 * Example: the promo card which appears in the voicemail tab.
 */
public class PromoCardViewHolder extends RecyclerView.ViewHolder {
    public static PromoCardViewHolder create(View rootView) {
        return new PromoCardViewHolder(rootView);
    }

    /**
     * The primary action button view.
     */
    private View mPrimaryActionView;

    /**
     * The secondary action button view.
     * The "Ok" button view.
     */
    private View mSecondaryActionView;

    /**
     * Creates an instance of the {@link ViewHolder}.
     *
     * @param rootView The root view.
     */
    private PromoCardViewHolder(View rootView) {
        super(rootView);

        mPrimaryActionView = rootView.findViewById(R.id.primary_action);
        mSecondaryActionView = rootView.findViewById(R.id.secondary_action);
    }

   /**
     * Retrieves the "primary" action button (eg. "OK").
     *
     * @return The view.
     */
    public View getPrimaryActionView() {
        return mPrimaryActionView;
    }

    /**
     * Retrieves the "secondary" action button (eg. "Cancel" or "More Info").
     *
     * @return The view.
     */
    public View getSecondaryActionView() {
        return mSecondaryActionView;
    }

    @NeededForTesting
    public static PromoCardViewHolder createForTest(Context context) {
        PromoCardViewHolder viewHolder = new PromoCardViewHolder(new View(context));
        viewHolder.mPrimaryActionView = new View(context);
        viewHolder.mSecondaryActionView = new View(context);
        return viewHolder;
    }
}
