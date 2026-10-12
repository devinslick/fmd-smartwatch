package com.devinslick.fmdring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.RemoteInput;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives FMD's command RESULTS. FMD's NotificationReplyTransport replies
 * through the command notification's RemoteInput action (result key "reply"),
 * so anything FMD sends back - locate coordinates, stats, confirmations -
 * lands here. We show it as a phone notification (with a geo: link for
 * locates) and push a compact form to the watchapp.
 */
public class ReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "FMDRing";
    private static final String CH_RESULT = "result";
    private static final int ID_RESULT = 3;
    private static final String REPLY_KEY = "reply";

    private static final Pattern P_LAT = Pattern.compile("Lat:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern P_LON = Pattern.compile("Lon:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern P_ACC = Pattern.compile("Accuracy:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern P_BAT = Pattern.compile("Battery:\\s*([0-9]+)");

    @Override
    public void onReceive(final Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) {
            return;
        }
        CharSequence cs = results.getCharSequence(REPLY_KEY);
        if (cs == null || cs.length() == 0) {
            return;
        }
        final String text = cs.toString();
        Log.d(TAG, "FMD result: " + text);

        postResultNotification(context, text);

        final PendingResult pending = goAsync();
        WatchPusher.send(context.getApplicationContext(), compactForWatch(text), new WatchPusher.Done() {
            @Override
            public void onDone() {
                pending.finish();
            }
        });
    }

    /** Compact form for the watch screen (~4 lines). */
    static String compactForWatch(String text) {
        String lat = group(P_LAT, text);
        String lon = group(P_LON, text);
        if (lat != null && lon != null) {
            String acc = group(P_ACC, text);
            String bat = group(P_BAT, text);
            StringBuilder sb = new StringBuilder();
            sb.append(lat).append('\n').append(lon).append('\n');
            if (acc != null) {
                sb.append("~").append(acc).append("m");
            }
            if (bat != null) {
                sb.append(" bat ").append(bat).append("%");
            }
            return sb.toString();
        }
        String s = text.trim();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    private static String group(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static void postResultNotification(Context ctx, String text) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CH_RESULT, "FMD results", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);

        Notification.Builder b = new Notification.Builder(ctx, CH_RESULT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("FMD result")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setTimeoutAfter(60000);

        String lat = group(P_LAT, text);
        String lon = group(P_LON, text);
        if (lat != null && lon != null) {
            Uri geo = Uri.parse("geo:" + lat + "," + lon + "?z=16");
            Intent map = new Intent(Intent.ACTION_VIEW, geo);
            PendingIntent pi = PendingIntent.getActivity(
                    ctx, 0, map,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(pi).setAutoCancel(true);
        }

        nm.notify(ID_RESULT, b.build());
    }
}
