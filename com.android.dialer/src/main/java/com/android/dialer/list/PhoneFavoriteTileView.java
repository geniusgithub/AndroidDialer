/*

 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.dialer.list;

import android.content.ClipData;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileView;
import com.android.dialer.R;

/**
 * A light version of the {@link com.android.contacts.common.list.ContactTileView} that is used in
 * Dialtacts for frequently called contacts. Slightly different behavior from superclass when you
 * tap it, you want to call the frequently-called number for the contact, even if that is not the
 * default number for that contact. This abstract class is the super class to both the row and tile
 * view.
 */
public abstract class PhoneFavoriteTileView extends ContactTileView {

    private static final String TAG = PhoneFavoriteTileView.class.getSimpleName();
    private static final boolean DEBUG = false;

    // These parameters instruct the photo manager to display the default image/letter at 70% of
    // its normal size, and vertically offset upwards 12% towards the top of the letter tile, to
    // make room for the contact name and number label at the bottom of the image.
    private static final float DEFAULT_IMAGE_LETTER_OFFSET = -0.12f;
    private static final float DEFAULT_IMAGE_LETTER_SCALE = 0.70f;

    /** View that contains the transparent shadow that is overlaid on top of the contact image. */
    private View mShadowOverlay;

    /** Users' most frequent phone number. */
    private String mPhoneNumberString;

    // Dummy clip data object that is attached to drag shadows so that text views
    // don't crash with an NPE if the drag shadow is released in their bounds
    private static final ClipData EMPTY_CLIP_DATA = ClipData.newPlainText("", "");

    // Constant to pass to the drag event so that the drag action only happens when a phone favorite
    // tile is long pressed.
    static final String DRAG_PHONE_FAVORITE_TILE = "PHONE_FAVORITE_TILE";

    public PhoneFavoriteTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShadowOverlay = findViewById(R.id.shadow_overlay);

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                final PhoneFavoriteTileView view = (PhoneFavoriteTileView) v;
                // NOTE The drag shadow is handled in the ListView.
                view.startDrag(EMPTY_CLIP_DATA, new View.DragShadowBuilder(),
                        DRAG_PHONE_FAVORITE_TILE, 0);
                return true;
            }
        });
    }

    @Override
    public void loadFromContact(ContactEntry entry) {
        super.loadFromContact(entry);
        // Set phone number to null in case we're reusing the view.
        mPhoneNumberString = null;
        if (entry != null) {
            // Grab the phone-number to call directly. See {@link onClick()}.
            mPhoneNumberString = entry.phoneNumber;

            // If this is a blank entry, don't show anything.
            // TODO krelease: Just hide the view for now. For this to truly look like an empty row
            // the entire ContactTileRow needs to be hidden.
            if (entry == ContactEntry.BLANK_ENTRY) {
                setVisibility(View.INVISIBLE);
            } else {
                final ImageView starIcon = (ImageView) findViewById(R.id.contact_star_icon);
                starIcon.setVisibility(entry.isFavorite ? View.VISIBLE : View.GONE);
                setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected boolean isDarkTheme() {
        return false;
    }

    @Override
    protected OnClickListener createClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener == null) {
                    return;
                }
                if (TextUtils.isEmpty(mPhoneNumberString)) {
                    // Copy "superclass" implementation
                    mListener.onContactSelected(getLookupUri(), MoreContactUtils
                            .getTargetRectFromView(PhoneFavoriteTileView.this));
                } else {
                    // When you tap a frequently-called contact, you want to
                    // call them at the number that you usually talk to them
                    // at (i.e. the one displayed in the UI), regardless of
                    // whether that's their default number.
                    mListener.onCallNumberDirectly(mPhoneNumberString);
                }
            }
        };
    }

    @Override
    protected DefaultImageRequest getDefaultImageRequest(String displayName, String lookupKey) {
        return new DefaultImageRequest(displayName, lookupKey, ContactPhotoManager.TYPE_DEFAULT,
                DEFAULT_IMAGE_LETTER_SCALE, DEFAULT_IMAGE_LETTER_OFFSET, false);
    }

    @Override
    protected void configureViewForImage(boolean isDefaultImage) {
        // Hide the shadow overlay if the image is a default image (i.e. colored letter tile)
        if (mShadowOverlay != null) {
            mShadowOverlay.setVisibility(isDefaultImage ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected boolean isContactPhotoCircular() {
        // Unlike Contacts' tiles, the Dialer's favorites tiles are square.
        return false;
    }
}
