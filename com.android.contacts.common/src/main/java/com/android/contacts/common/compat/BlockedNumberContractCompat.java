package com.android.contacts.common.compat;

import android.content.Context;
import android.provider.BlockedNumberContract;

public class BlockedNumberContractCompat {
    public static boolean canCurrentUserBlockNumbers(Context context) {
        return BlockedNumberContract.canCurrentUserBlockNumbers(context);
    }
}