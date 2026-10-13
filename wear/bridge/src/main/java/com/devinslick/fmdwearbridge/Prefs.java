package com.devinslick.fmdwearbridge;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings. Plain prefs: same uid-only trust domain as the fmdring bridge. */
public final class Prefs {

    public static final String NAME = "fmdwearbridge";
    public static final String TRIGGER = "trigger";   // FMD trigger word, default "fmd"
    public static final String PIN = "pin";           // FMD notification password

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
}
