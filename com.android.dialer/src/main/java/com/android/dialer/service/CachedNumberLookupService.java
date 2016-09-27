package com.android.dialer.service;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.dialer.calllog.ContactInfo;

import java.io.InputStream;

public interface CachedNumberLookupService {

    public interface CachedContactInfo {
        public static final int SOURCE_TYPE_DIRECTORY = 1;
        public static final int SOURCE_TYPE_EXTENDED = 2;
        public static final int SOURCE_TYPE_PLACES = 3;
        public static final int SOURCE_TYPE_PROFILE = 4;
        public static final int SOURCE_TYPE_CNAP = 5;

        public ContactInfo getContactInfo();

        public void setSource(int sourceType, String name, long directoryId);
        public void setDirectorySource(String name, long directoryId);
        public void setExtendedSource(String name, long directoryId);
        public void setLookupKey(String lookupKey);
    }

    public CachedContactInfo buildCachedContactInfo(ContactInfo info);

    /**
     * Perform a lookup using the cached number lookup service to return contact
     * information stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link CachedContactInfo} containing the contact information if the phone
     * number is found in the cache, {@link ContactInfo#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    public CachedContactInfo lookupCachedContactFromNumber(Context context, String number);

    public void addContact(Context context, CachedContactInfo info);

    public boolean isCacheUri(String uri);

    public boolean isBusiness(int sourceType);
    public boolean canReportAsInvalid(int sourceType, String objectId);

    /**
     * @return return {@link Uri} to the photo or return {@code null} when failing to add photo
     */
    public @Nullable Uri addPhoto(Context context, String number, InputStream in);

    /**
     * Remove all cached phone number entries from the cache, regardless of how old they
     * are.
     *
     * @param context Valid context
     */
    public void clearAllCacheEntries(Context context);
}
