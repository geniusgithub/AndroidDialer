/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.contactinfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.Log;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.Assert;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.IOException;
/**
 * Class to create the appropriate contact icon from a ContactInfo.
 * This class is for synchronous, blocking calls to generate bitmaps, while
 * ContactCommons.ContactPhotoManager is to cache, manage and update a ImageView asynchronously.
 */
public class ContactPhotoLoader {

    private static final String TAG = "ContactPhotoLoader";

    private final Context mContext;
    private final ContactInfo mContactInfo;

    public ContactPhotoLoader(Context context, ContactInfo contactInfo) {
        mContext = Preconditions.checkNotNull(context);
        mContactInfo = Preconditions.checkNotNull(contactInfo);
    }

    /**
     * Create a contact photo icon bitmap appropriate for the ContactInfo.
     */
    public Bitmap loadPhotoIcon() {
        Assert.assertNotUiThread("ContactPhotoLoader#loadPhotoIcon called on UI thread");
        int photoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);
        return drawableToBitmap(getIcon(), photoSize, photoSize);
    }

    @VisibleForTesting
    Drawable getIcon() {
        Drawable drawable = createPhotoIconDrawable();
        if (drawable == null) {
            drawable = createLetterTileDrawable();
        }
        return drawable;
    }

    /**
     * @return a {@link Drawable} of  circular photo icon if the photo can be loaded, {@code null}
     * otherwise.
     */
    @Nullable
    private Drawable createPhotoIconDrawable() {
        if (mContactInfo.photoUri == null) {
            return null;
        }
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(),
                    mContactInfo.photoUri);
            final RoundedBitmapDrawable drawable =
                    RoundedBitmapDrawableFactory.create(mContext.getResources(), bitmap);
            drawable.setAntiAlias(true);
            drawable.setCornerRadius(bitmap.getHeight() / 2);
            return drawable;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }

    /**
     * @return a {@link LetterTileDrawable} based on the ContactInfo.
     */
    private Drawable createLetterTileDrawable() {
        LetterTileDrawable drawable = new LetterTileDrawable(mContext.getResources());
        drawable.setIsCircular(true);
        ContactInfoHelper helper =
                new ContactInfoHelper(mContext, GeoUtil.getCurrentCountryIso(mContext));
        if (helper.isBusiness(mContactInfo.sourceType)) {
            drawable.setContactType(LetterTileDrawable.TYPE_BUSINESS);
        }
        drawable.setLetterAndColorFromContactDetails(mContactInfo.name, mContactInfo.lookupKey);
        return drawable;
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

}
