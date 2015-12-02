/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridView;
import android.widget.ImageView;

import com.android.dialer.R;
import com.android.dialer.list.DragDropController.DragItemContainer;

/**
 * Viewgroup that presents the user's speed dial contacts in a grid.
 */
public class PhoneFavoriteListView extends GridView implements OnDragDropListener,
        DragItemContainer {

    public static final String LOG_TAG = PhoneFavoriteListView.class.getSimpleName();

    private float mTouchSlop;

    private int mTopScrollBound;
    private int mBottomScrollBound;
    private int mLastDragY;

    private Handler mScrollHandler;
    private final long SCROLL_HANDLER_DELAY_MILLIS = 5;
    private final int DRAG_SCROLL_PX_UNIT = 25;

    private boolean mIsDragScrollerRunning = false;
    private int mTouchDownForDragStartX;
    private int mTouchDownForDragStartY;

    private Bitmap mDragShadowBitmap;
    private ImageView mDragShadowOverlay;
    private View mDragShadowParent;
    private int mAnimationDuration;

    final int[] mLocationOnScreen = new int[2];

    // X and Y offsets inside the item from where the user grabbed to the
    // child's left coordinate. This is used to aid in the drawing of the drag shadow.
    private int mTouchOffsetToChildLeft;
    private int mTouchOffsetToChildTop;

    private int mDragShadowLeft;
    private int mDragShadowTop;

    private DragDropController mDragDropController = new DragDropController(this);

    private final float DRAG_SHADOW_ALPHA = 0.7f;

    /**
     * {@link #mTopScrollBound} and {@link mBottomScrollBound} will be
     * offseted to the top / bottom by {@link #getHeight} * {@link #BOUND_GAP_RATIO} pixels.
     */
    private final float BOUND_GAP_RATIO = 0.2f;

    private final Runnable mDragScroller = new Runnable() {
        @Override
        public void run() {
            if (mLastDragY <= mTopScrollBound) {
                smoothScrollBy(-DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
            } else if (mLastDragY >= mBottomScrollBound) {
                smoothScrollBy(DRAG_SCROLL_PX_UNIT, (int) SCROLL_HANDLER_DELAY_MILLIS);
            }
            mScrollHandler.postDelayed(this, SCROLL_HANDLER_DELAY_MILLIS);
        }
    };

    private final AnimatorListenerAdapter mDragShadowOverAnimatorListener =
            new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            if (mDragShadowBitmap != null) {
                mDragShadowBitmap.recycle();
                mDragShadowBitmap = null;
            }
            mDragShadowOverlay.setVisibility(GONE);
            mDragShadowOverlay.setImageBitmap(null);
        }
    };

    public PhoneFavoriteListView(Context context) {
        this(context, null);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimationDuration = context.getResources().getInteger(R.integer.fade_duration);
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mDragDropController.addOnDragDropListener(this);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
    }

    /**
     * TODO: This is all swipe to remove code (nothing to do with drag to remove). This should
     * be cleaned up and removed once drag to remove becomes the only way to remove contacts.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownForDragStartX = (int) ev.getX();
            mTouchDownForDragStartY = (int) ev.getY();
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        final int action = event.getAction();
        final int eX = (int) event.getX();
        final int eY = (int) event.getY();
        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED: {
                if (!PhoneFavoriteTileView.DRAG_PHONE_FAVORITE_TILE.equals(event.getLocalState())) {
                    // Ignore any drag events that were not propagated by long pressing
                    // on a {@link PhoneFavoriteTileView}
                    return false;
                }
                if (!mDragDropController.handleDragStarted(eX, eY)) {
                    return false;
                }
                break;
            }
            case DragEvent.ACTION_DRAG_LOCATION:
                mLastDragY = eY;
                mDragDropController.handleDragHovered(this, eX, eY);
                // Kick off {@link #mScrollHandler} if it's not started yet.
                if (!mIsDragScrollerRunning &&
                        // And if the distance traveled while dragging exceeds the touch slop
                        (Math.abs(mLastDragY - mTouchDownForDragStartY) >= 4 * mTouchSlop)) {
                    mIsDragScrollerRunning = true;
                    ensureScrollHandler();
                    mScrollHandler.postDelayed(mDragScroller, SCROLL_HANDLER_DELAY_MILLIS);
                }
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                final int boundGap = (int) (getHeight() * BOUND_GAP_RATIO);
                mTopScrollBound = (getTop() + boundGap);
                mBottomScrollBound = (getBottom() - boundGap);
                break;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DROP:
                ensureScrollHandler();
                mScrollHandler.removeCallbacks(mDragScroller);
                mIsDragScrollerRunning = false;
                // Either a successful drop or it's ended with out drop.
                if (action == DragEvent.ACTION_DROP || action == DragEvent.ACTION_DRAG_ENDED) {
                    mDragDropController.handleDragFinished(eX, eY, false);
                }
                break;
            default:
                break;
        }
        // This ListView will consume the drag events on behalf of its children.
        return true;
    }

    public void setDragShadowOverlay(ImageView overlay) {
        mDragShadowOverlay = overlay;
        mDragShadowParent = (View) mDragShadowOverlay.getParent();
    }

    /**
     * Find the view under the pointer.
     */
    private View getViewAtPosition(int x, int y) {
        final int count = getChildCount();
        View child;
        for (int childIdx = 0; childIdx < count; childIdx++) {
            child = getChildAt(childIdx);
            if (y >= child.getTop() && y <= child.getBottom() && x >= child.getLeft()
                    && x <= child.getRight()) {
                return child;
            }
        }
        return null;
    }

    private void ensureScrollHandler() {
        if (mScrollHandler == null) {
            mScrollHandler = getHandler();
        }
    }

    public DragDropController getDragDropController() {
        return mDragDropController;
    }

    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView tileView) {
        if (mDragShadowOverlay == null) {
            return;
        }

        mDragShadowOverlay.clearAnimation();
        mDragShadowBitmap = createDraggedChildBitmap(tileView);
        if (mDragShadowBitmap == null) {
            return;
        }

        tileView.getLocationOnScreen(mLocationOnScreen);
        mDragShadowLeft = mLocationOnScreen[0];
        mDragShadowTop = mLocationOnScreen[1];

        // x and y are the coordinates of the on-screen touch event. Using these
        // and the on-screen location of the tileView, calculate the difference between
        // the position of the user's finger and the position of the tileView. These will
        // be used to offset the location of the drag shadow so that it appears that the
        // tileView is positioned directly under the user's finger.
        mTouchOffsetToChildLeft = x - mDragShadowLeft;
        mTouchOffsetToChildTop = y - mDragShadowTop;

        mDragShadowParent.getLocationOnScreen(mLocationOnScreen);
        mDragShadowLeft -= mLocationOnScreen[0];
        mDragShadowTop -= mLocationOnScreen[1];

        mDragShadowOverlay.setImageBitmap(mDragShadowBitmap);
        mDragShadowOverlay.setVisibility(VISIBLE);
        mDragShadowOverlay.setAlpha(DRAG_SHADOW_ALPHA);

        mDragShadowOverlay.setX(mDragShadowLeft);
        mDragShadowOverlay.setY(mDragShadowTop);
    }

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView tileView) {
        // Update the drag shadow location.
        mDragShadowParent.getLocationOnScreen(mLocationOnScreen);
        mDragShadowLeft = x - mTouchOffsetToChildLeft - mLocationOnScreen[0];
        mDragShadowTop = y - mTouchOffsetToChildTop - mLocationOnScreen[1];
        // Draw the drag shadow at its last known location if the drag shadow exists.
        if (mDragShadowOverlay != null) {
            mDragShadowOverlay.setX(mDragShadowLeft);
            mDragShadowOverlay.setY(mDragShadowTop);
        }
    }

    @Override
    public void onDragFinished(int x, int y) {
        if (mDragShadowOverlay != null) {
            mDragShadowOverlay.clearAnimation();
            mDragShadowOverlay.animate().alpha(0.0f)
                    .setDuration(mAnimationDuration)
                    .setListener(mDragShadowOverAnimatorListener)
                    .start();
        }
    }

    @Override
    public void onDroppedOnRemove() {}

    private Bitmap createDraggedChildBitmap(View view) {
        view.setDrawingCacheEnabled(true);
        final Bitmap cache = view.getDrawingCache();

        Bitmap bitmap = null;
        if (cache != null) {
            try {
                bitmap = cache.copy(Bitmap.Config.ARGB_8888, false);
            } catch (final OutOfMemoryError e) {
                Log.w(LOG_TAG, "Failed to copy bitmap from Drawing cache", e);
                bitmap = null;
            }
        }

        view.destroyDrawingCache();
        view.setDrawingCacheEnabled(false);

        return bitmap;
    }

    @Override
    public PhoneFavoriteSquareTileView getViewForLocation(int x, int y) {
        getLocationOnScreen(mLocationOnScreen);
        // Calculate the X and Y coordinates of the drag event relative to the view
        final int viewX = x - mLocationOnScreen[0];
        final int viewY = y - mLocationOnScreen[1];
        final View child = getViewAtPosition(viewX, viewY);

        if (!(child instanceof PhoneFavoriteSquareTileView)) {
            return null;
        }

        return (PhoneFavoriteSquareTileView) child;
    }
}
