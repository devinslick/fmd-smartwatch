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

import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives FMD command RESULTS for the Wear path: FMD's reply through the
 * command notification's RemoteInput action (result key "reply"). Shows a
 * phone notification (geo: link for locates) and forwards the text to the
 * requesting watch node on /fmd-result.
 */
public class WearReplyReceiver extends BroadcastReceiver {

    public static final String EXTRA_NODE = "node";
    public static final String PATH_RESULT = "/fmd-result";

    private static final String CH_RESULT = "result";
    private static final int ID_RESULT = 3;
    private static final String REPLY_KEY = "reply";

    private static final Pattern P_LAT = Pattern.compile("Lat:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern P_LON = Pattern.compile("Lon:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

    @Override
    public void onReceive(final Context context, Intent intent) {
        android.os.Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) {
            return;
        }
        CharSequence cs = results.getCharSequence(REPLY_KEY);
        if (cs == null || cs.length() == 0) {
            return;
        }
        final String text = cs.toString();

        postResultNotification(context, text);

        final String node = intent.getStringExtra(EXTRA_NODE);
        if (node == null) {
            return;
        }
        final PendingResult pending = goAsync();
        Wearable.getMessageClient(context.getApplicationContext())
                .sendMessage(node, PATH_RESULT, text.getBytes(StandardCharsets.UTF_8))
                .addOnSuccessListener(result -> pending.finish())
                .addOnFailureListener(e -> pending.finish());
    }

    private void postResultNotification(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CH_RESULT, "FMD results", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(ch);

        Notification.Builder b = new Notification.Builder(context, CH_RESULT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("FMD result")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text));

        Matcher lat = P_LAT.matcher(text);
        Matcher lon = P_LON.matcher(text);
        if (lat.find() && lon.find()) {
            Uri geo = Uri.parse("geo:" + lat.group(1) + "," + lon.group(1) + "?q="
                    + lat.group(1) + "," + lon.group(1));
            b.setContentIntent(PendingIntent.getActivity(
                    context, 0, new Intent(Intent.ACTION_VIEW, geo),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        nm.notify(ID_RESULT, b.build());
    }
}
