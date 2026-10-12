package com.devinslick.fmdring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;

/**
 * Posts the two notifications that drive the Pebble -> FMD bridge.
 *
 * 1. A persistent "trigger" notification whose contentIntent is a broadcast to
 *    RingReceiver. The Pebble app mirrors it; selecting it on the watch fires
 *    the PendingIntent. A broadcast (not an activity) is used deliberately so
 *    Android 10+ background-activity-launch restrictions never apply.
 *
 * 2. A transient "command" notification whose body text starts with the FMD
 *    trigger word + notification password. FMD's NotificationListenService
 *    executes it and dismisses the notification itself.
 *
 * Commands are composed from settings: "<trigger> <pin> <suffix>".
 */
public final class Notifier {

    private static final String CH_TRIGGER = "trigger";
    private static final String CH_COMMAND = "command";
    public static final int ID_TRIGGER = 1;
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

    public static String ringCommand(Context ctx) {
        return composeCommand(ctx, "ring");
    }

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel trigger = new NotificationChannel(
                CH_TRIGGER, "Find phone button", NotificationManager.IMPORTANCE_LOW);
        trigger.setDescription("Persistent launcher notification. Select it on the Pebble to ring the phone.");
        NotificationChannel command = new NotificationChannel(
                CH_COMMAND, "FMD commands", NotificationManager.IMPORTANCE_MIN);
        command.setDescription("Transient FMD command carrier notifications.");
        nm.createNotificationChannels(Arrays.asList(trigger, command));
    }

    /** Persistent, watch-visible notification. contentIntent -> RingReceiver. */
    public static Notification buildTriggerNotification(Context ctx) {
        ensureChannels(ctx);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, 0, new Intent(ctx, RingReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(ctx, CH_TRIGGER)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Find phone")
                .setContentText("Select to ring (via FMD)")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    public static void postTriggerNotification(Context ctx) {
        Notification n = buildTriggerNotification(ctx);
        ctx.getSystemService(NotificationManager.class).notify(ID_TRIGGER, n);
    }

    /** The FMD command carrier. Body MUST start with the FMD trigger word.
     *  Carries a RemoteInput reply action so FMD's result comes back to
     *  ReplyReceiver (PendingIntent MUST be mutable for RemoteInput fill-in). */
    public static void postCommandNotification(Context ctx, String command) {
        ensureChannels(ctx);

        android.app.RemoteInput ri = new android.app.RemoteInput.Builder("reply")
                .setLabel("Result")
                .build();
        PendingIntent replyPi = PendingIntent.getBroadcast(
                ctx, 2, new Intent(ctx, ReplyReceiver.class),
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
