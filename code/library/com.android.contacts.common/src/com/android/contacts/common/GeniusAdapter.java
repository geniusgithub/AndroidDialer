package com.android.contacts.common;

import com.android.contacts.common.util.PermissionsUtil;

import android.telephony.PhoneNumberUtils;
import android.text.style.TtsSpan;

public class GeniusAdapter {
	public static String createTtsSpannable(String input){
		if (!PermissionsUtil.sIsAtLeastM){
			return input;
		}
		String result =  (String) PhoneNumberUtils.createTtsSpannable(input.toString());
		 return result;
	}
}
