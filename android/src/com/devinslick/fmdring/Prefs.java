package com.devinslick.fmdring;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings. Plain prefs: same uid-only trust domain as before. */
public final class Prefs {

    public static final String NAME = "fmdring";
    public static final String TRIGGER = "trigger";   // FMD trigger word, default "fmd"
    public static final String PIN = "pin";           // FMD notification password
    public static final String DEBUG = "pk2debug";    // PK2 diagnostic notifications

    private Prefs() {
    }

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getTrigger(Context ctx) {
        return get(ctx).getString(TRIGGER, "fmd");
    }

    public static String getPin(Context ctx) {
        return get(ctx).getString(PIN, "");
    }

    public static boolean getDebug(Context ctx) {
        return get(ctx).getBoolean(DEBUG, false);
    }
}
