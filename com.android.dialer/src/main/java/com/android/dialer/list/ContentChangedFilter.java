package com.android.dialer.list;

import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

/**
 * AccessibilityDelegate that will filter out TYPE_WINDOW_CONTENT_CHANGED
 * Used to suppress "Showing items x of y" from firing of ListView whenever it's content changes.
 * AccessibilityEvent can only be rejected at a view's parent once it is generated,
 * use addToParent() to add this delegate to the parent.
 */
public class ContentChangedFilter extends AccessibilityDelegate {
  //the view we don't want TYPE_WINDOW_CONTENT_CHANGED to fire.
  private View mView;

  /**
   * Add this delegate to the parent of @param view to filter out TYPE_WINDOW_CONTENT_CHANGED
   */
  public static void addToParent(View view){
    View parent = (View) view.getParent();
    parent.setAccessibilityDelegate(new ContentChangedFilter(view));
  }

  private ContentChangedFilter(View view){
    super();
    mView = view;
  }
  @Override
  public boolean onRequestSendAccessibilityEvent (ViewGroup host, View child, AccessibilityEvent event){
    if(child == mView){
      if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
        return false;
      }
    }
    return super.onRequestSendAccessibilityEvent(host,child,event);
  }

}
