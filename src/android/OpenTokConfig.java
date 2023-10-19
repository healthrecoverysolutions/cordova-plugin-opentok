package com.tokbox.cordova;

import android.text.TextUtils;

import androidx.annotation.NonNull;

public class OpenTokConfig {
    /*
    Fill the following variables using your own Project info from the OpenTok dashboard
    https://dashboard.tokbox.com/projects
    */

    // Replace with a API key
    public static final String API_KEY = "45343572";

    // Replace with a generated Session ID
    public static final String SESSION_ID = "2_MX40NTM0MzU3Mn5-MTY5NzY4Mzg0Njk0NH5vUWZ1WW9hOHpsaVNreW1kQXlXT29yQ21-fn4";

    // Replace with a generated token (from the dashboard or using an OpenTok server SDK)
    public static final String TOKEN = "T1==cGFydG5lcl9pZD00NTM0MzU3MiZzaWc9MWQ0NTIwMmQ2ZmQzOTkwNTk1YTEwZjEyZDgwMmRjNGMwY2VjMDAzNTpzZXNzaW9uX2lkPTJfTVg0ME5UTTBNelUzTW41LU1UWTVOelk0TXpnME5qazBOSDV2VVdaMVdXOWhPSHBzYVZOcmVXMWtRWGxYVDI5eVEyMS1mbjQmY3JlYXRlX3RpbWU9MTY5NzY4Mzg0NiZyb2xlPXB1Ymxpc2hlciZub25jZT0xNjk3NjgzODQ2Ljk5MTE5Mjg5MDc0MzAmaW5pdGlhbF9sYXlvdXRfY2xhc3NfbGlzdD0=";

    public static boolean isValid() {
        if (TextUtils.isEmpty(OpenTokConfig.API_KEY)
                || TextUtils.isEmpty(OpenTokConfig.SESSION_ID)
                || TextUtils.isEmpty(OpenTokConfig.TOKEN)) {
            return false;
        }

        return true;
    }

    @NonNull
    public static String getDescription() {
        return "OpenTokConfig:" + "\n"
                + "API_KEY: " + OpenTokConfig.API_KEY + "\n"
                + "SESSION_ID: " + OpenTokConfig.SESSION_ID + "\n"
                + "TOKEN: " + OpenTokConfig.TOKEN + "\n";
    }
}
