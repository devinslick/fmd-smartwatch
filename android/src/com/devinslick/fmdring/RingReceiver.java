package com.devinslick.fmdring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * THE trigger. Exported; anyone may fire:
 *
 *   adb shell am broadcast -a com.devinslick.fmdring.action.RING \
 *       -n com.devinslick.fmdring/.RingReceiver
 *
 * Optional extra "command" is the FMD command SUFFIX; the full command is
 * composed with the trigger word and notification password from settings:
 *   --es command locate     ->   "fmd <pin> locate"
 *
 * Sources that fire this today:
 *  - the persistent "Find phone" notification's contentIntent (Pebble-select / tap)
 *  - any Android app / automation / shell (explicit intent or the action above)
 *  - FmdPebbleService, when the FMD Ring watchapp sends an AppMessage
 */
public class RingReceiver extends BroadcastReceiver {

    public static final String ACTION_RING = "com.devinslick.fmdring.action.RING";
    public static final String EXTRA_COMMAND = "command";

    @Override
    public void onReceive(Context context, Intent intent) {
        String suffix = intent.getStringExtra(EXTRA_COMMAND);
        if (suffix == null || suffix.isEmpty()) {
            suffix = "ring";
        }
        Notifier.postCommandNotification(context, Notifier.composeCommand(context, suffix));
    }
}
