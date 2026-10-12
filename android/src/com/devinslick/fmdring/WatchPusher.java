package com.devinslick.fmdring;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.rebble.pebblekit2.common.SendDataCallback;
import io.rebble.pebblekit2.common.UniversalRequestResponse;

/**
 * Pushes an AppMessage to the FMD Ring watchapp via the Pebble app's
 * PebbleKit 2 sender service (client side of the same
 * UniversalRequestResponse binder our companion service serves).
 *
 * Fire-and-forget with a timeout; never blocks the caller.
 */
public final class WatchPusher {

    public static final String ACTION_SEND = "io.rebble.pebblekit2.SEND_DATA_TO_WATCH";
    private static final String TAG = "FMDRing";
    private static final String WATCHAPP_UUID = "39084eff-db47-473c-b1af-eb44fdbaa8b5";
    /** messageKeys auto-assign from 10000: Cmd=10000, Result=10001 (see pbw appinfo.json). */
    private static final String KEY_RESULT_IDX = "10001";

    public interface Done {
        void onDone();
    }

    private WatchPusher() {
    }

    public static void send(final Context appCtx, final String text, final Done done) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Handler timeout = new Handler(Looper.getMainLooper());

        final Runnable finishOnce = new Runnable() {
            @Override
            public void run() {
                if (finished.compareAndSet(false, true)) {
                    timeout.removeCallbacksAndMessages(null);
                    done.onDone();
                }
            }
        };

        String pkg = findPebbleApp(appCtx);
        if (pkg == null) {
            Log.w(TAG, "WatchPusher: no Pebble app with sender service found");
            finishOnce.run();
            return;
        }

        final ServiceConnection[] holder = new ServiceConnection[1];
        holder[0] = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, final IBinder service) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            UniversalRequestResponse api =
                                    UniversalRequestResponse.Stub.asInterface(service);

                            Bundle dict = new Bundle();
                            dict.putString(KEY_RESULT_IDX + "_TYPE", "String");
                            dict.putString(KEY_RESULT_IDX + "_VALUE", text);

                            Bundle data = new Bundle();
                            data.putString("ACTION", "SEND_DATA_TO_WATCH");
                            data.putString("WATCHAPP_UUID", WATCHAPP_UUID);
                            data.putBundle("DATA_DICTIONARY", dict);

                            api.request(data, new SendDataCallback.Stub() {
                                @Override
                                public void onResult(Bundle bundle) {
                                    Log.d(TAG, "WatchPusher result: " + bundle);
                                    try {
                                        appCtx.unbindService(holder[0]);
                                    } catch (Exception ignored) {
                                    }
                                    finishOnce.run();
                                }
                            });
                        } catch (Exception e) {
                            Log.w(TAG, "WatchPusher transact failed", e);
                            try {
                                appCtx.unbindService(holder[0]);
                            } catch (Exception ignored) {
                            }
                            finishOnce.run();
                        }
                    }
                }, "fmd-watch-push").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // binding dropped; nothing else to do
            }
        };

        Intent i = new Intent(ACTION_SEND).setPackage(pkg);
        boolean ok = appCtx.bindService(i, holder[0], Context.BIND_AUTO_CREATE);
        if (!ok) {
            Log.w(TAG, "WatchPusher: bindService failed for " + pkg);
            finishOnce.run();
            return;
        }

        timeout.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.w(TAG, "WatchPusher: timeout");
                try {
                    appCtx.unbindService(holder[0]);
                } catch (Exception ignored) {
                }
                finishOnce.run();
            }
        }, 6000);
    }

    private static String findPebbleApp(Context ctx) {
        List<ResolveInfo> svcs = ctx.getPackageManager()
                .queryIntentServices(new Intent(ACTION_SEND), 0);
        if (svcs == null) {
            return null;
        }
        for (ResolveInfo ri : svcs) {
            if (ri.serviceInfo == null) {
                continue;
            }
            String p = ri.serviceInfo.packageName.toLowerCase();
            if (p.contains("pebble") || p.contains("rebble")) {
                return ri.serviceInfo.packageName;
            }
        }
        return null;
    }
}
