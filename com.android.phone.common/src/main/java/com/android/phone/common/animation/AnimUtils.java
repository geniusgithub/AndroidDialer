/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.common.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;

import com.android.phone.common.compat.PathInterpolatorCompat;

import java.lang.Float;

public class AnimUtils {
    public static final int DEFAULT_DURATION = -1;
    public static final int NO_DELAY = 0;

    public static final Interpolator EASE_IN = PathInterpolatorCompat.create(
            0.0f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator EASE_OUT = PathInterpolatorCompat.create(
            0.4f, 0.0f, 1.0f, 1.0f);
    public static final Interpolator EASE_OUT_EASE_IN = PathInterpolatorCompat.create(
            0.4f, 0, 0.2f, 1);

    public static class AnimationCallback {
        public void onAnimationEnd() {}
        public void onAnimationCancel() {}
    }

    public static void crossFadeViews(View fadeIn, View fadeOut, int duration) {
        fadeIn(fadeIn, duration);
        fadeOut(fadeOut, duration);
    }

    public static void fadeOut(View fadeOut, int duration) {
        fadeOut(fadeOut, duration, null);
    }

    public static void fadeOut(final View fadeOut, int durationMs,
            final AnimationCallback callback) {
        fadeOut.setAlpha(1);
        final ViewPropertyAnimator animator = fadeOut.animate();
        animator.cancel();
        animator.alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fadeOut.setVisibility(View.GONE);
                if (callback != null) {
                    callback.onAnimationEnd();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                fadeOut.setVisibility(View.GONE);
                fadeOut.setAlpha(0);
                if (callback != null) {
                    callback.onAnimationCancel();
                }
            }
        });
        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.start();
    }

    public static void fadeIn(View fadeIn, int durationMs) {
        fadeIn(fadeIn, durationMs, NO_DELAY, null);
    }

    public static void fadeIn(final View fadeIn, int durationMs, int delay,
            final AnimationCallback callback) {
        fadeIn.setAlpha(0);
        final ViewPropertyAnimator animator = fadeIn.animate();
        animator.cancel();

        animator.setStartDelay(delay);
        animator.alpha(1).withLayer().setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                fadeIn.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                fadeIn.setAlpha(1);
                if (callback != null) {
                    callback.onAnimationCancel();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (callback != null) {
                    callback.onAnimationEnd();
                }
            }
        });
        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.start();
    }

    /**
     * Scales in the view from scale of 0 to actual dimensions.
     * @param view The view to scale.
     * @param durationMs The duration of the scaling in milliseconds.
     * @param startDelayMs The delay to applying the scaling in milliseconds.
     */
    public static void scaleIn(final View view, int durationMs, int startDelayMs) {
        AnimatorListenerAdapter listener = (new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                view.setScaleX(1);
                view.setScaleY(1);
            }
        });
        scaleInternal(view, 0 /* startScaleValue */, 1 /* endScaleValue */, durationMs,
                startDelayMs, listener, EASE_IN);
    }


    /**
     * Scales out the view from actual dimensions to 0.
     * @param view The view to scale.
     * @param durationMs The duration of the scaling in milliseconds.
     */
    public static void scaleOut(final View view, int durationMs) {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                view.setVisibility(View.GONE);
                view.setScaleX(0);
                view.setScaleY(0);
            }
        };

        scaleInternal(view, 1 /* startScaleValue */, 0 /* endScaleValue */, durationMs,
                NO_DELAY, listener, EASE_OUT);
    }

    private static void scaleInternal(final View view, int startScaleValue, int endScaleValue,
            int durationMs, int startDelay, AnimatorListenerAdapter listener,
            Interpolator interpolator) {
        view.setScaleX(startScaleValue);
        view.setScaleY(startScaleValue);

        final ViewPropertyAnimator animator = view.animate();
        animator.cancel();

        animator.setInterpolator(interpolator)
            .scaleX(endScaleValue)
            .scaleY(endScaleValue)
            .setListener(listener)
            .withLayer();

        if (durationMs != DEFAULT_DURATION) {
            animator.setDuration(durationMs);
        }
        animator.setStartDelay(startDelay);

        animator.start();
    }

    /**
     * Animates a view to the new specified dimensions.
     * @param view The view to change the dimensions of.
     * @param newWidth The new width of the view.
     * @param newHeight The new height of the view.
     */
    public static void changeDimensions(final View view, final int newWidth, final int newHeight) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

        final int oldWidth = view.getWidth();
        final int oldHeight = view.getHeight();
        final int deltaWidth = newWidth - oldWidth;
        final int deltaHeight = newHeight - oldHeight;

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                Float value = (Float) animator.getAnimatedValue();

                view.getLayoutParams().width = (int) (value * deltaWidth + oldWidth);
                view.getLayoutParams().height = (int) (value * deltaHeight + oldHeight);
                view.requestLayout();
            }
        });
        animator.start();
    }
}
