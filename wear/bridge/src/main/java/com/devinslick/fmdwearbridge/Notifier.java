package com.devinslick.fmdwearbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;

/**
 * Posts the transient "command" notification whose body text starts with the
 * FMD trigger word + notification password. FMD's NotificationListenService
 * executes it and dismisses the notification itself. Carries a RemoteInput
 * reply action so FMD's result comes back to WearReplyReceiver (the
 * PendingIntent MUST be mutable for RemoteInput fill-in).
 *
 * Ported from the fmdring bridge (android/src/com/devinslick/fmdring/Notifier);
 * keep the two in sync.
 */
public final class Notifier {

    private static final String CH_COMMAND = "command";
    private static final int ID_COMMAND = 2;

    private Notifier() {
    }

    /** Compose a full FMD command from settings: trigger + pin + suffix. */
    public static String composeCommand(Context ctx, String suffix) {
        StringBuilder sb = new StringBuilder(Prefs.getTrigger(ctx));
        String pin = Prefs.getPin(ctx);
        if (pin != null && !pin.isEmpty()) {
            sb.append(' ').append(pin);
        }
        if (suffix != null && !suffix.isEmpty()) {
            sb.append(' ').append(suffix);
        }
        return sb.toString();
    }

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel command = new NotificationChannel(
                CH_COMMAND, "FMD commands", NotificationManager.IMPORTANCE_MIN);
        command.setDescription("Transient FMD command carrier notifications.");
        nm.createNotificationChannels(Arrays.asList(command));
    }

    /**
     * The FMD command carrier. Body MUST start with the FMD trigger word.
     * replyNodeId rides along so the result can be routed back to the watch.
     */
    public static void postCommandNotification(Context ctx, String command, String replyNodeId) {
        ensureChannels(ctx);

        android.app.RemoteInput ri = new android.app.RemoteInput.Builder("reply")
                .setLabel("Result")
                .build();
        Intent replyIntent = new Intent(ctx, WearReplyReceiver.class);
        if (replyNodeId != null) {
            replyIntent.putExtra(WearReplyReceiver.EXTRA_NODE, replyNodeId);
        }
        PendingIntent replyPi = PendingIntent.getBroadcast(
                ctx, 2, replyIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action action = new Notification.Action.Builder(
                R.drawable.ic_notif, "Result", replyPi)
                .addRemoteInput(ri)
                .build();

        Notification n = new Notification.Builder(ctx, CH_COMMAND)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("FMD")
                .setContentText(command)
                .setTimeoutAfter(4000)
                .addAction(action)
                .build();
        ctx.getSystemService(NotificationManager.class).notify(ID_COMMAND, n);
    }
}
