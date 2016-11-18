/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.telecom.PhoneAccount;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.R;

/**
 * Constructs shortcut intents.
 */
public class ShortcutIntentBuilder {

    private static final String[] CONTACT_COLUMNS = {
        Contacts.DISPLAY_NAME,
        Contacts.PHOTO_ID,
        Contacts.LOOKUP_KEY
    };

    private static final int CONTACT_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 1;
    private static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 2;

    private static final String[] PHONE_COLUMNS = {
        Phone.DISPLAY_NAME,
        Phone.PHOTO_ID,
        Phone.NUMBER,
        Phone.TYPE,
        Phone.LABEL,
        Phone.LOOKUP_KEY
    };

    private static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int PHONE_PHOTO_ID_COLUMN_INDEX = 1;
    private static final int PHONE_NUMBER_COLUMN_INDEX = 2;
    private static final int PHONE_TYPE_COLUMN_INDEX = 3;
    private static final int PHONE_LABEL_COLUMN_INDEX = 4;
    private static final int PHONE_LOOKUP_KEY_COLUMN_INDEX = 5;

    private static final String[] PHOTO_COLUMNS = {
        Photo.PHOTO,
    };

    private static final int PHOTO_PHOTO_COLUMN_INDEX = 0;

    private static final String PHOTO_SELECTION = Photo._ID + "=?";

    private final OnShortcutIntentCreatedListener mListener;
    private final Context mContext;
    private int mIconSize;
    private final int mIconDensity;
    private final int mOverlayTextBackgroundColor;
    private final Resources mResources;

    /**
     * This is a hidden API of the launcher in JellyBean that allows us to disable the animation
     * that it would usually do, because it interferes with our own animation for QuickContact.
     * This is needed since some versions of the launcher override the intent flags and therefore
     * ignore Intent.FLAG_ACTIVITY_NO_ANIMATION.
     */
    public static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    /**
     * Listener interface.
     */
    public interface OnShortcutIntentCreatedListener {

        /**
         * Callback for shortcut intent creation.
         *
         * @param uri the original URI for which the shortcut intent has been
         *            created.
         * @param shortcutIntent resulting shortcut intent.
         */
        void onShortcutIntentCreated(Uri uri, Intent shortcutIntent);
    }

    public ShortcutIntentBuilder(Context context, OnShortcutIntentCreatedListener listener) {
        mContext = context;
        mListener = listener;

        mResources = context.getResources();
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        mIconSize = mResources.getDimensionPixelSize(R.dimen.shortcut_icon_size);
        if (mIconSize == 0) {
            mIconSize = am.getLauncherLargeIconSize();
        }
        mIconDensity = am.getLauncherLargeIconDensity();
        mOverlayTextBackgroundColor = mResources.getColor(R.color.shortcut_overlay_text_background);
    }

    public void createContactShortcutIntent(Uri contactUri) {
        new ContactLoadingAsyncTask(contactUri).execute();
    }

    public void createPhoneNumberShortcutIntent(Uri dataUri, String shortcutAction) {
        new PhoneNumberLoadingAsyncTask(dataUri, shortcutAction).execute();
    }

    /**
     * An asynchronous task that loads name, photo and other data from the database.
     */
    private abstract class LoadingAsyncTask extends AsyncTask<Void, Void, Void> {
        protected Uri mUri;
        protected String mContentType;
        protected String mDisplayName;
        protected String mLookupKey;
        protected byte[] mBitmapData;
        protected long mPhotoId;

        public LoadingAsyncTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mContentType = mContext.getContentResolver().getType(mUri);
            loadData();
            loadPhoto();
            return null;
        }

        protected abstract void loadData();

        private void loadPhoto() {
            if (mPhotoId == 0) {
                return;
            }

            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(Data.CONTENT_URI, PHOTO_COLUMNS, PHOTO_SELECTION,
                    new String[] { String.valueOf(mPhotoId) }, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mBitmapData = cursor.getBlob(PHOTO_PHOTO_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
    }

    private final class ContactLoadingAsyncTask extends LoadingAsyncTask {
        public ContactLoadingAsyncTask(Uri uri) {
            super(uri);
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, CONTACT_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(CONTACT_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
                        mLookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        @Override
        protected void onPostExecute(Void result) {
            createContactShortcutIntent(mUri, mContentType, mDisplayName, mLookupKey, mBitmapData);
        }
    }

    private final class PhoneNumberLoadingAsyncTask extends LoadingAsyncTask {
        private final String mShortcutAction;
        private String mPhoneNumber;
        private int mPhoneType;
        private String mPhoneLabel;

        public PhoneNumberLoadingAsyncTask(Uri uri, String shortcutAction) {
            super(uri);
            mShortcutAction = shortcutAction;
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, PHONE_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(PHONE_PHOTO_ID_COLUMN_INDEX);
                        mPhoneNumber = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                        mPhoneType = cursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        mPhoneLabel = cursor.getString(PHONE_LABEL_COLUMN_INDEX);
                        mLookupKey = cursor.getString(PHONE_LOOKUP_KEY_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            createPhoneNumberShortcutIntent(mUri, mDisplayName, mLookupKey, mBitmapData,
                    mPhoneNumber, mPhoneType, mPhoneLabel, mShortcutAction);
        }
    }

    private Drawable getPhotoDrawable(byte[] bitmapData, String displayName, String lookupKey) {
        if (bitmapData != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length, null);
            return new BitmapDrawable(mContext.getResources(), bitmap);
        } else {
            return ContactPhotoManager.getDefaultAvatarDrawableForContact(mContext.getResources(),
                    false, new DefaultImageRequest(displayName, lookupKey, false));
        }
    }

    private void createContactShortcutIntent(Uri contactUri, String contentType, String displayName,
            String lookupKey, byte[] bitmapData) {
        Drawable drawable = getPhotoDrawable(bitmapData, displayName, lookupKey);

        // Use an implicit intent without a package name set. It is reasonable for a disambiguation
        // dialog to appear when opening QuickContacts from the launcher. Plus, this will be more
        // resistant to future package name changes done to Contacts.
        Intent shortcutIntent = new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT);

        // When starting from the launcher, start in a new, cleared task.
        // CLEAR_WHEN_TASK_RESET cannot reset the root of a task, so we
        // clear the whole thing preemptively here since QuickContactActivity will
        // finish itself when launching other detail activities. We need to use
        // Intent.FLAG_ACTIVITY_NO_ANIMATION since not all versions of launcher will respect
        // the INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION intent extra.
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        // Tell the launcher to not do its animation, because we are doing our own
        shortcutIntent.putExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION, true);

        shortcutIntent.setDataAndType(contactUri, contentType);
        shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_EXCLUDE_MIMES,
                (String[]) null);

        final Bitmap icon = generateQuickContactIcon(drawable);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        if (TextUtils.isEmpty(displayName)) {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, mContext.getResources().getString(
                    R.string.missing_name));
        } else {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);
        }

        mListener.onShortcutIntentCreated(contactUri, intent);
    }

    private void createPhoneNumberShortcutIntent(Uri uri, String displayName, String lookupKey,
            byte[] bitmapData, String phoneNumber, int phoneType, String phoneLabel,
            String shortcutAction) {
        Drawable drawable = getPhotoDrawable(bitmapData, displayName, lookupKey);

        Bitmap bitmap;
        Uri phoneUri;
        if (Intent.ACTION_CALL.equals(shortcutAction)) {
            // Make the URI a direct tel: URI so that it will always continue to work
            phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);
            bitmap = generatePhoneNumberIcon(drawable, phoneType, phoneLabel,
                    R.drawable.ic_call);
        } else {
            phoneUri = Uri.fromParts(ContactsUtils.SCHEME_SMSTO, phoneNumber, null);
            bitmap = generatePhoneNumberIcon(drawable, phoneType, phoneLabel,
                    R.drawable.ic_message_24dp);
        }

        Intent shortcutIntent = new Intent(shortcutAction, phoneUri);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);

        if (TextUtils.isEmpty(displayName)) {
            displayName = mContext.getResources().getString(R.string.missing_name);
        }
        if (TextUtils.equals(shortcutAction, Intent.ACTION_CALL)) {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    mContext.getResources().getString(R.string.call_by_shortcut, displayName));
        } else if (TextUtils.equals(shortcutAction, Intent.ACTION_SENDTO)) {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    mContext.getResources().getString(R.string.sms_by_shortcut, displayName));
        }

        mListener.onShortcutIntentCreated(uri, intent);
    }

    private Bitmap generateQuickContactIcon(Drawable photo) {

        // Setup the drawing classes
        Bitmap bitmap = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Copy in the photo
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        photo.setBounds(dst);
        photo.draw(canvas);

        // Draw the icon with a rounded border
        RoundedBitmapDrawable roundedDrawable =
                RoundedBitmapDrawableFactory.create(mResources, bitmap);
        roundedDrawable.setAntiAlias(true);
        roundedDrawable.setCornerRadius(mIconSize / 2);
        Bitmap roundedBitmap = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(roundedBitmap);
        roundedDrawable.setBounds(dst);
        roundedDrawable.draw(canvas);
        canvas.setBitmap(null);

        return roundedBitmap;
    }

    /**
     * Generates a phone number shortcut icon. Adds an overlay describing the type of the phone
     * number, and if there is a photo also adds the call action icon.
     */
    private Bitmap generatePhoneNumberIcon(Drawable photo, int phoneType, String phoneLabel,
            int actionResId) {
        final Resources r = mContext.getResources();
        final float density = r.getDisplayMetrics().density;

        Bitmap phoneIcon = ((BitmapDrawable) r.getDrawableForDensity(actionResId, mIconDensity))
                .getBitmap();

        Bitmap icon = generateQuickContactIcon(photo);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);

        // Create an overlay for the phone number type
        CharSequence overlay = Phone.getTypeLabel(r, phoneType, phoneLabel);

        if (overlay != null) {
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setTextSize(r.getDimension(R.dimen.shortcut_overlay_text_size));
            textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
            textPaint.setShadowLayer(4f, 0, 2f, r.getColor(R.color.textColorIconOverlayShadow));

            final FontMetricsInt fmi = textPaint.getFontMetricsInt();

            // First fill in a darker background around the text to be drawn
            final Paint workPaint = new Paint();
            workPaint.setColor(mOverlayTextBackgroundColor);
            workPaint.setStyle(Paint.Style.FILL);
            final int textPadding = r
                    .getDimensionPixelOffset(R.dimen.shortcut_overlay_text_background_padding);
            final int textBandHeight = (fmi.descent - fmi.ascent) + textPadding * 2;
            dst.set(0, mIconSize - textBandHeight, mIconSize, mIconSize);
            canvas.drawRect(dst, workPaint);

            overlay = TextUtils.ellipsize(overlay, textPaint, mIconSize, TruncateAt.END);
            final float textWidth = textPaint.measureText(overlay, 0, overlay.length());
            canvas.drawText(overlay, 0, overlay.length(), (mIconSize - textWidth) / 2, mIconSize
                    - fmi.descent - textPadding, textPaint);
        }

        // Draw the phone action icon as an overlay
        Rect src = new Rect(0, 0, phoneIcon.getWidth(), phoneIcon.getHeight());
        int iconWidth = icon.getWidth();
        dst.set(iconWidth - ((int) (20 * density)), -1,
                iconWidth, ((int) (19 * density)));
        canvas.drawBitmap(phoneIcon, src, dst, photoPaint);

        canvas.setBitmap(null);

        return icon;
    }
}
