package com.devinslick.fmdring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-posts the persistent trigger notification after a reboot. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Notifier.postTriggerNotification(context);
        }
    }
}
