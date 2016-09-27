package com.android.dialer.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.R;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items:
 * 1) Directly calling the phone number query
 * 2) Adding the phone number query to a contact
 *
 * These shortcuts can be enabled or disabled to toggle whether or not they show up in the
 * list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {

    private String mFormattedQueryString;
    private String mCountryIso;

    public final static int SHORTCUT_INVALID = -1;
    public final static int SHORTCUT_DIRECT_CALL = 0;
    public final static int SHORTCUT_CREATE_NEW_CONTACT = 1;
    public final static int SHORTCUT_ADD_TO_EXISTING_CONTACT = 2;
    public final static int SHORTCUT_SEND_SMS_MESSAGE = 3;
    public final static int SHORTCUT_MAKE_VIDEO_CALL = 4;
    public final static int SHORTCUT_BLOCK_NUMBER = 5;

    public final static int SHORTCUT_COUNT = 6;

    private final boolean[] mShortcutEnabled = new boolean[SHORTCUT_COUNT];

    private final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();
    private boolean mVideoCallingEnabled = false;

    public DialerPhoneNumberListAdapter(Context context) {
        super(context);

        mCountryIso = GeoUtil.getCurrentCountryIso(context);
        mVideoCallingEnabled = CallUtil.isVideoEnabled(context);
    }

    @Override
    public int getCount() {
        return super.getCount() + getShortcutCount();
    }

    /**
     * @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT
     */
    public int getShortcutCount() {
        int count = 0;
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            if (mShortcutEnabled[i]) count++;
        }
        return count;
    }

    public void disableAllShortcuts() {
        for (int i = 0; i < mShortcutEnabled.length; i++) {
            mShortcutEnabled[i] = false;
        }
    }

    @Override
    public int getItemViewType(int position) {
        final int shortcut = getShortcutTypeFromPosition(position);
        if (shortcut >= 0) {
            // shortcutPos should always range from 1 to SHORTCUT_COUNT
            return super.getViewTypeCount() + shortcut;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public int getViewTypeCount() {
        // Number of item view types in the super implementation + 2 for the 2 new shortcuts
        return super.getViewTypeCount() + SHORTCUT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            if (convertView != null) {
                assignShortcutToView((ContactListItemView) convertView, shortcutType);
                return convertView;
            } else {
                final ContactListItemView v = new ContactListItemView(getContext(), null,
                        mVideoCallingEnabled);
                assignShortcutToView(v, shortcutType);
                return v;
            }
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = super.newView(context, partition, cursor, position,
                parent);

        view.setSupportVideoCallIcon(mVideoCallingEnabled);
        return view;
    }

    /**
     * @param position The position of the item
     * @return The enabled shortcut type matching the given position if the item is a
     * shortcut, -1 otherwise
     */
    public int getShortcutTypeFromPosition(int position) {
        int shortcutCount = position - super.getCount();
        if (shortcutCount >= 0) {
            // Iterate through the array of shortcuts, looking only for shortcuts where
            // mShortcutEnabled[i] is true
            for (int i = 0; shortcutCount >= 0 && i < mShortcutEnabled.length; i++) {
                if (mShortcutEnabled[i]) {
                    shortcutCount--;
                    if (shortcutCount < 0) return i;
                }
            }
            throw new IllegalArgumentException("Invalid position - greater than cursor count "
                    + " but not a shortcut.");
        }
        return SHORTCUT_INVALID;
    }

    @Override
    public boolean isEmpty() {
        return getShortcutCount() == 0 && super.isEmpty();
    }

    @Override
    public boolean isEnabled(int position) {
        final int shortcutType = getShortcutTypeFromPosition(position);
        if (shortcutType >= 0) {
            return true;
        } else {
            return super.isEnabled(position);
        }
    }

    private void assignShortcutToView(ContactListItemView v, int shortcutType) {
        final CharSequence text;
        final int drawableId;
        final Resources resources = getContext().getResources();
        final String number = getFormattedQueryString();
        switch (shortcutType) {
            case SHORTCUT_DIRECT_CALL:
                text = ContactDisplayUtils.getTtsSpannedPhoneNumber(resources,
                        R.string.search_shortcut_call_number,
                        mBidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR));
                drawableId = R.drawable.ic_search_phone;
                break;
            case SHORTCUT_CREATE_NEW_CONTACT:
                text = resources.getString(R.string.search_shortcut_create_new_contact);
                drawableId = R.drawable.ic_search_add_contact;
                break;
            case SHORTCUT_ADD_TO_EXISTING_CONTACT:
                text = resources.getString(R.string.search_shortcut_add_to_contact);
                drawableId = R.drawable.ic_person_24dp;
                break;
            case SHORTCUT_SEND_SMS_MESSAGE:
                text = resources.getString(R.string.search_shortcut_send_sms_message);
                drawableId = R.drawable.ic_message_24dp;
                break;
            case SHORTCUT_MAKE_VIDEO_CALL:
                text = resources.getString(R.string.search_shortcut_make_video_call);
                drawableId = R.drawable.ic_videocam;
                break;
            case SHORTCUT_BLOCK_NUMBER:
                text = resources.getString(R.string.search_shortcut_block_number);
                drawableId = R.drawable.ic_not_interested_googblue_24dp;
                break;
            default:
                throw new IllegalArgumentException("Invalid shortcut type");
        }
        v.setDrawableResource(drawableId);
        v.setDisplayName(text);
        v.setPhotoPosition(super.getPhotoPosition());
        v.setAdjustSelectionBoundsEnabled(false);
    }

    /**
     * @return True if the shortcut state (disabled vs enabled) was changed by this operation
     */
    public boolean setShortcutEnabled(int shortcutType, boolean visible) {
        final boolean changed = mShortcutEnabled[shortcutType] != visible;
        mShortcutEnabled[shortcutType] = visible;
        return changed;
    }

    public String getFormattedQueryString() {
        return mFormattedQueryString;
    }

    @Override
    public void setQueryString(String queryString) {
        mFormattedQueryString = PhoneNumberUtils.formatNumber(
                PhoneNumberUtils.normalizeNumber(queryString), mCountryIso);
        super.setQueryString(queryString);
    }
}
