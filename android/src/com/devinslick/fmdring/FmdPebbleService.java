package com.devinslick.fmdring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import io.rebble.pebblekit2.common.SendDataCallback;
import io.rebble.pebblekit2.common.UniversalRequestResponse;

/**
 * PebbleKit 2 companion service (new RePebble Pebble app).
 *
 * Wire contract replicated from pebble-dev/PebbleKitAndroid2:
 *   Bundle keys: ACTION, WATCHAPP_UUID, WATCH_ID, DATA_DICTIONARY
 *   Dictionary bundle entries: "<idx>_TYPE" ("String"|"Int32"|...) / "<idx>_VALUE"
 *   Reply bundle: TRANSMISSION_RESULTS -> { TYPE: "ACK" | "NACK" }
 *
 * The watchapp sends an AppMessage with a single String key holding the FMD
 * command suffix ("ring", "locate", ...). We compose the full command from
 * settings and post it. Commands only from Pebble-app callers; PK2 diagnostic
 * notifications are opt-in via settings.
 */
public class FmdPebbleService extends Service {

    private static final String TAG = "FMDRing";
    private static final String CH_DEBUG = "pk2debug";

    private static final String KEY_ACTION = "ACTION";
    private static final String KEY_WATCHAPP_UUID = "WATCHAPP_UUID";
    private static final String KEY_DATA_DICTIONARY = "DATA_DICTIONARY";
    private static final String ACTION_RECEIVE_DATA = "ACTION_RECEIVE_DATA_FROM_WATCH";
    private static final String KEY_RESULTS = "TRANSMISSION_RESULTS";
    private static final String KEY_TYPE = "TYPE";

    private final UniversalRequestResponse.Stub binder = new UniversalRequestResponse.Stub() {
        @Override
        public void request(Bundle data, SendDataCallback callback) {
            String caller = callerName();
            String action = data == null ? null : data.getString(KEY_ACTION);
            String uuid = data == null ? null : data.getString(KEY_WATCHAPP_UUID);
            String suffix = parseCommandSuffix(data == null ? null : data.getBundle(KEY_DATA_DICTIONARY));
            Log.d(TAG, "PK2 request: action=" + action + " uuid=" + uuid
                    + " cmd=" + suffix + " caller=" + caller);

            boolean ok = ACTION_RECEIVE_DATA.equals(action) && caller != null && suffix != null;

            if (ok && Prefs.getDebug(FmdPebbleService.this)) {
                postDebug("req: " + action + "\ncmd=" + suffix + "\nuuid=" + uuid + "\ncaller=" + caller);
            }

            if (ok) {
                Notifier.postCommandNotification(
                        FmdPebbleService.this,
                        Notifier.composeCommand(FmdPebbleService.this, suffix));
            }

            Bundle reply = new Bundle();
            Bundle result = new Bundle();
            result.putString(KEY_TYPE, ok ? "ACK" : "NACK");
            reply.putBundle(KEY_RESULTS, result);
            if (callback != null) {
                try {
                    callback.onResult(reply);
                } catch (Exception e) {
                    Log.w(TAG, "callback failed", e);
                }
            }
        }
    };

    /**
     * Extract the command suffix from the dictionary bundle. Our watchapp sends
     * exactly one String key ("Cmd"); older builds sent a single Int key (ring).
     * Sanitized to [A-Za-z0-9 ].
     */
    private static String parseCommandSuffix(Bundle dict) {
        if (dict == null) {
            return null;
        }
        String cmd = null;
        boolean sawNumber = false;
        for (String k : dict.keySet()) {
            if (!k.endsWith("_TYPE")) {
                continue;
            }
            String idx = k.substring(0, k.length() - 5);
            String type = dict.getString(k);
            if ("String".equals(type)) {
                Object v = dict.get(idx + "_VALUE");
                if (v instanceof String) {
                    cmd = (String) v;
                }
            } else if (type != null && (type.startsWith("Int") || type.startsWith("UInt"))) {
                sawNumber = true;
            }
        }
        if (cmd == null) {
            return sawNumber ? "ring" : null;
        }
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == ' ') {
                clean.append(c);
            }
        }
        String s = clean.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String callerName() {
        try {
            String[] pkgs = getPackageManager().getPackagesForUid(Binder.getCallingUid());
            if (pkgs == null || pkgs.length == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (String p : pkgs) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void postDebug(String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CH_DEBUG, "PK2 diagnostics", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, CH_DEBUG)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("FMD Ring PK2")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setTimeoutAfter(15000)
                    .build();
            nm.notify(99, n);
        } catch (Exception e) {
            Log.w(TAG, "postDebug failed", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "PK2 companion bound: " + intent);
        if (Prefs.getDebug(this)) {
            postDebug("bound: " + intent.getAction());
        }
        return binder;
    }
}
