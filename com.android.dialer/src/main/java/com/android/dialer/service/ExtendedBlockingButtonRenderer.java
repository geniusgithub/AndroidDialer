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

package com.android.dialer.service;

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewStub;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.util.List;

/**
 * Interface responsible for rendering spam buttons.
 */
public interface ExtendedBlockingButtonRenderer {

    final class ViewHolderInfo {

        public final List<View> completeListItemViews;
        public final List<View> extendedBlockedViews;
        public final List<View> blockedNumberViews;
        public final String phoneNumber;
        public final String countryIso;
        public final String nameOrNumber;
        public final String displayNumber;

        public ViewHolderInfo(
                /* All existing views amongst the list item actions, even if invisible */
                List<View> completeListItemViews,
                /* Views that should be seen if the number is in the blacklist */
                List<View> extendedBlockedViews,
                /* Views that should be seen if the number is in the extended blacklist */
                List<View> blockedNumberViews,
                String phoneNumber,
                String countryIso,
                String nameOrNumber,
                String displayNumber) {

            this.completeListItemViews = completeListItemViews;
            this.extendedBlockedViews = extendedBlockedViews;
            this.blockedNumberViews = blockedNumberViews;
            this.phoneNumber = phoneNumber;
            this.countryIso = countryIso;
            this.nameOrNumber = nameOrNumber;
            this.displayNumber = displayNumber;
        }
    }

    interface Listener {
        void onBlockedNumber(String number, @Nullable String countryIso);
        void onUnblockedNumber(String number, @Nullable String countryIso);
    }

    /**
     * Renders buttons for a phone number.
     */
    void render(ViewStub viewStub);

    void setViewHolderInfo(ViewHolderInfo info);

    /**
     * Updates the photo and label for the given phone number and country iso.
     *
     * @param number Phone number for which the rendering occurs.
     * @param countryIso Two-letter country code.
     * @param badge {@link QuickContactBadge} in which the photo should be rendered.
     * @param view Textview that will hold the new label.
     */
    void updatePhotoAndLabelIfNecessary(
            String number, String countryIso, QuickContactBadge badge, TextView view);
}
