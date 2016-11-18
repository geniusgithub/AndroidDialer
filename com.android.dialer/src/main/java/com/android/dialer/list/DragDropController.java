package com.android.dialer.list;

import android.util.Log;
import android.view.View;

import com.android.contacts.common.compat.CompatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles and combines drag events generated from multiple views, and then fires
 * off events to any OnDragDropListeners that have registered for callbacks.
 */
public class DragDropController {

    private final List<OnDragDropListener> mOnDragDropListeners =
            new ArrayList<OnDragDropListener>();
    private final DragItemContainer mDragItemContainer;
    private final int[] mLocationOnScreen = new int[2];

    /**
     * Callback interface used to retrieve views based on the current touch coordinates of the
     * drag event. The {@link DragItemContainer} houses the draggable views that this
     * {@link DragDropController} controls.
     */
    public interface DragItemContainer {
        public PhoneFavoriteSquareTileView getViewForLocation(int x, int y);
    }

    public DragDropController(DragItemContainer dragItemContainer) {
        mDragItemContainer = dragItemContainer;
    }

    /**
     * @return True if the drag is started, false if the drag is cancelled for some reason.
     */
    boolean handleDragStarted(View v, int x, int y) {
        int screenX = x;
        int screenY = y;
        // The coordinates in dragEvent of DragEvent.ACTION_DRAG_STARTED before NYC is window-related.
        // This is fixed in NYC.
        if (CompatUtils.isNCompatible()) {
            v.getLocationOnScreen(mLocationOnScreen);
            screenX = x + mLocationOnScreen[0];
            screenY = y + mLocationOnScreen[1];
        }
        final PhoneFavoriteSquareTileView tileView = mDragItemContainer.getViewForLocation(
                screenX, screenY);
        if (tileView == null) {
            return false;
        }
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragStarted(screenX, screenY, tileView);
        }

        return true;
    }

    public void handleDragHovered(View v, int x, int y) {
        v.getLocationOnScreen(mLocationOnScreen);
        final int screenX = x + mLocationOnScreen[0];
        final int screenY = y + mLocationOnScreen[1];
        final PhoneFavoriteSquareTileView view = mDragItemContainer.getViewForLocation(
                screenX, screenY);
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragHovered(screenX, screenY, view);
        }
    }

    public void handleDragFinished(int x, int y, boolean isRemoveView) {
        if (isRemoveView) {
            for (int i = 0; i < mOnDragDropListeners.size(); i++) {
                mOnDragDropListeners.get(i).onDroppedOnRemove();
            }
        }

        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragFinished(x, y);
        }
    }

    public void addOnDragDropListener(OnDragDropListener listener) {
        if (!mOnDragDropListeners.contains(listener)) {
            mOnDragDropListeners.add(listener);
        }
    }

    public void removeOnDragDropListener(OnDragDropListener listener) {
        if (mOnDragDropListeners.contains(listener)) {
            mOnDragDropListeners.remove(listener);
        }
    }

}
