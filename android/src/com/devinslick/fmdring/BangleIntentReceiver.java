package com.devinslick.fmdring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Bangle.js path: Gadgetbridge forwards the watch app's
 * {t:'intent', target:'broadcast', action:..., cmd:'ring'} messages as
 * real Android broadcasts (requires the user to enable Gadgetbridge's
 * experimental "Allow intents" setting). We read the cmd extra and post
 * the FMD command notification exactly like the other transports.
 *
 * Result routing back to the watch is not possible through Gadgetbridge
 * intents (one-way); the phone result notification is the feedback
 * surface for now.
 */
public class BangleIntentReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.devinslick.fmdring.action.BANGLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String cmd = intent.getStringExtra("cmd");
        if (cmd == null || cmd.isEmpty()) {
            return;
        }
        String full = Notifier.composeCommand(context, cmd);
        Notifier.postCommandNotification(context, full);
    }
}
