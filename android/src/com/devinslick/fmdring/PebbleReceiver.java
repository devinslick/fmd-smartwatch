package com.devinslick.fmdring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Companion receiver for the FMD Ring Pebble watchapp.
 *
 * The classic Pebble Android app delivers a watchapp's AppMessages to
 * third-party Android apps as a broadcast (com.getpebble.action.app_received)
 * with the sending app's UUID as an extra. We match OUR watchapp UUID and
 * post the FMD command notification. No JS, no HTTP, no service.
 */
public class PebbleReceiver extends BroadcastReceiver {

    /** Must match the watchapp's package.json UUID. */
    private static final UUID WATCHAPP_UUID =
            UUID.fromString("39084eff-db47-473c-b1af-eb44fdbaa8b5");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!uuidMatches(intent)) {
            return;
        }
        Notifier.postCommandNotification(context, Notifier.ringCommand(context));
    }

    private static boolean uuidMatches(Intent intent) {
        // UUID extra has shipped in multiple forms across Pebble app versions.
        if (intent.getExtras() == null) {
            return false;
        }
        Object extra = intent.getExtras().get("uuid");
        if (extra == null) {
            return false;
        }
        try {
            if (extra instanceof UUID) {
                return WATCHAPP_UUID.equals(extra);
            }
            if (extra instanceof String) {
                return WATCHAPP_UUID.equals(UUID.fromString((String) extra));
            }
            if (extra instanceof byte[]) {
                byte[] b = (byte[]) extra;
                if (b.length != 16) {
                    return false;
                }
                ByteBuffer bb = ByteBuffer.wrap(b);
                long msb = bb.getLong();
                long lsb = bb.getLong();
                return WATCHAPP_UUID.equals(new UUID(msb, lsb));
            }
        } catch (IllegalArgumentException ignored) {
            // unparseable uuid form -> not ours
        }
        return false;
    }
}
