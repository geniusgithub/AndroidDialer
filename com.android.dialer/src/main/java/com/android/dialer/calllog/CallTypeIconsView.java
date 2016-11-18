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

package com.android.dialer.calllog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.testing.NeededForTesting;
import com.android.contacts.common.util.BitmapUtil;
import com.android.dialer.R;
import com.android.dialer.util.AppCompatConstants;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
    private boolean mShowVideo = false;
    private int mWidth;
    private int mHeight;

    private static Resources sResources;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (sResources == null) {
          sResources = new Resources(context);
        }
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + sResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    /**
     * Determines whether the video call icon will be shown.
     *
     * @param showVideo True where the video icon should be shown.
     */
    public void setShowVideo(boolean showVideo) {
        mShowVideo = showVideo;
        if (showVideo) {
            mWidth += sResources.videoCall.getIntrinsicWidth();
            mHeight = Math.max(mHeight, sResources.videoCall.getIntrinsicHeight());
            invalidate();
        }
    }

    /**
     * Determines if the video icon should be shown.
     *
     * @return True if the video icon should be shown.
     */
    public boolean isVideoShown() {
        return mShowVideo;
    }

    @NeededForTesting
    public int getCount() {
        return mCallTypes.size();
    }

    @NeededForTesting
    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case AppCompatConstants.CALLS_INCOMING_TYPE:
                return sResources.incoming;
            case AppCompatConstants.CALLS_OUTGOING_TYPE:
                return sResources.outgoing;
            case AppCompatConstants.CALLS_MISSED_TYPE:
                return sResources.missed;
            case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
                return sResources.voicemail;
            case AppCompatConstants.CALLS_BLOCKED_TYPE:
                return sResources.blocked;
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return sResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + sResources.iconMargin;
        }

        // If showing the video call icon, draw it scaled appropriately.
        if (mShowVideo) {
            final Drawable drawable = sResources.videoCall;
            final int right = left + sResources.videoCall.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, sResources.videoCall.getIntrinsicHeight());
            drawable.draw(canvas);
        }
    }

    private static class Resources {

        // Drawable representing an incoming answered call.
        public final Drawable incoming;

        // Drawable respresenting an outgoing call.
        public final Drawable outgoing;

        // Drawable representing an incoming missed call.
        public final Drawable missed;

        // Drawable representing a voicemail.
        public final Drawable voicemail;

        // Drawable representing a blocked call.
        public final Drawable blocked;

        //  Drawable repesenting a video call.
        public final Drawable videoCall;

        /**
         * The margin to use for icons.
         */
        public final int iconMargin;

        /**
         * Configures the call icon drawables.
         * A single white call arrow which points down and left is used as a basis for all of the
         * call arrow icons, applying rotation and colors as needed.
         *
         * @param context The current context.
         */
        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();

            incoming = r.getDrawable(R.drawable.ic_call_arrow);
            incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            // Create a rotated instance of the call arrow for outgoing calls.
            outgoing = BitmapUtil.getRotatedDrawable(r, R.drawable.ic_call_arrow, 180f);
            outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);

            // Need to make a copy of the arrow drawable, otherwise the same instance colored
            // above will be recolored here.
            missed = r.getDrawable(R.drawable.ic_call_arrow).mutate();
            missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);

            voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);

            blocked = getScaledBitmap(context, R.drawable.ic_block_24dp);
            blocked.setColorFilter(r.getColor(R.color.blocked_call), PorterDuff.Mode.MULTIPLY);

            videoCall = getScaledBitmap(context, R.drawable.ic_videocam_24dp);
            videoCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
                    PorterDuff.Mode.MULTIPLY);

            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }

        // Gets the icon, scaled to the height of the call type icons. This helps display all the
        // icons to be the same height, while preserving their width aspect ratio.
        private Drawable getScaledBitmap(Context context, int resourceId) {
            Bitmap icon = BitmapFactory.decodeResource(context.getResources(), resourceId);
            int scaledHeight =
                    context.getResources().getDimensionPixelSize(R.dimen.call_type_icon_size);
            int scaledWidth = (int) ((float) icon.getWidth()
                    * ((float) scaledHeight / (float) icon.getHeight()));
            Bitmap scaledIcon = Bitmap.createScaledBitmap(icon, scaledWidth, scaledHeight, false);
            return new BitmapDrawable(context.getResources(), scaledIcon);
        }
    }
}
