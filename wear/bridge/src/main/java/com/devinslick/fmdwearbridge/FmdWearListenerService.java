package com.devinslick.fmdwearbridge;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

/**
 * Wear Data Layer entry point: receives raw command suffixes from the
 * FMD Remote watch app on /fmd-cmd, composes trigger + pin + command and
 * posts the notification FMD trusts. Results come back through
 * WearReplyReceiver and are forwarded to the watch on /fmd-result.
 */
public class FmdWearListenerService extends WearableListenerService {

    public static final String PATH_CMD = "/fmd-cmd";

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!PATH_CMD.equals(event.getPath())) {
            return;
        }
        String cmd = new String(event.getData(), StandardCharsets.UTF_8);
        String full = Notifier.composeCommand(this, cmd);
        Notifier.postCommandNotification(this, full, event.getSourceNodeId());
    }
}
