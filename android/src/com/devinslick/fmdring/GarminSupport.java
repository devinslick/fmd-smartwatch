package com.devinslick.fmdring;

import android.content.Context;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;

import java.util.List;
import java.util.Map;

/**
 * Garmin Connect IQ path: the FMD Remote watch app's
 * Communications.transmit() payloads are relayed by the Garmin Connect
 * app to the ConnectIQ Android SDK, which delivers them here. The watch
 * sends a Dictionary with a "cmd" string; on the phone it arrives as a
 * Map. Same command vocabulary as the other transports; the bridge
 * composes trigger word + pin + command into the notification FMD
 * trusts.
 *
 * Initialization is lazy (MainActivity / BootReceiver) — no persistent
 * process of our own; the SDK binds to Garmin Connect for delivery.
 * Requires Garmin Connect installed, and messages flow only while the
 * FMD Remote app is in the foreground on the watch (Connect IQ apps
 * receive messages only while running).
 */
public final class GarminSupport {

    /** Must match the watch app's manifest.xml / monkey.jungle UUID. */
    public static final String APP_UUID = "adfb5368-67db-463f-91e5-4d45341f5c09";

    private static volatile boolean initialized = false;

    private GarminSupport() {
    }

    public static synchronized void init(final Context context) {
        if (initialized) {
            return;
        }
        initialized = true;
        final ConnectIQ sdk = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS);
        sdk.initialize(context, false, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkShutDown() {
                initialized = false; // allow re-init on next startup path
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus status) {
                // Garmin Connect missing/outdated — Garmin path stays dormant.
            }

            @Override
            public void onSdkReady() {
                try {
                    List<IQDevice> devices = sdk.getKnownDevices();
                    if (devices == null) {
                        return;
                    }
                    final Context appCtx = context.getApplicationContext();
                    final IQApp app = new IQApp(APP_UUID);
                    for (final IQDevice device : devices) {
                        sdk.registerForAppEvents(device, app,
                                new ConnectIQ.IQApplicationEventListener() {
                                    @Override
                                    public void onMessageReceived(IQDevice device, IQApp app,
                                                                  List<Object> messages,
                                                                  ConnectIQ.IQMessageStatus status) {
                                        if (messages == null) {
                                            return;
                                        }
                                        for (Object m : messages) {
                                            if (m instanceof Map) {
                                                handle(appCtx, (Map) m);
                                            }
                                        }
                                    }
                                });
                    }
                } catch (Exception ignored) {
                    // SDK not ready / Garmin Connect unavailable.
                }
            }
        });
    }

    private static void handle(Context ctx, Map payload) {
        Object cmd = payload.get("cmd");
        if (cmd instanceof String && !((String) cmd).isEmpty()) {
            String full = Notifier.composeCommand(ctx, (String) cmd);
            Notifier.postCommandNotification(ctx, full);
        }
    }
}
