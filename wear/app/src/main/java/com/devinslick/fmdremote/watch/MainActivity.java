package com.devinslick.fmdremote.watch;

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;

/**
 * FMD Remote — Wear OS watch app (draft).
 *
 * Sends FMD command strings to the phone bridge ("FMD Watch Bridge" /
 * com.devinslick.fmdwearbridge) over the Wear Data Layer and shows the
 * result the bridge sends back. The bridge composes trigger word + pin +
 * command into the notification FMD trusts; it holds the credentials.
 *
 * Same action set as the Pebble remote; keep the two lists in sync.
 */
public class MainActivity extends ListActivity
        implements MessageClient.OnMessageReceivedListener {

    /** Capability advertised by the phone bridge (see bridge res/values/wear.xml). */
    private static final String CAPABILITY = "fmd_bridge";
    private static final String PATH_CMD = "/fmd-cmd";
    private static final String PATH_RESULT = "/fmd-result";

    private static final String[] LABELS = {
            "Ring", "Ring long", "Locate", "Locate last", "Locate GPS",
            "Stats", "Lock", "Flash", "Ringer silent", "Ringer normal",
            "DND on", "DND off", "BT scan", "Camera front", "Camera back",
    };

    /** Raw command suffixes; the bridge prepends trigger word + pin. */
    private static final String[] CMDS = {
            "ring", "ring long", "locate", "locate last", "locate gps",
            "stats", "lock", "flash", "ringermode silent", "ringermode normal",
            "nodisturb on", "nodisturb off", "bluetoothscan", "camera front", "camera back",
    };

    private TextView status;
    private String bridgeNodeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = (TextView) findViewById(R.id.status);
        setListAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, LABELS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.getMessageClient(this).addListener(this);
        findBridge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.getMessageClient(this).removeListener(this);
    }

    private void findBridge() {
        status.setText(R.string.status_searching);
        Wearable.getCapabilityClient(this)
                .getCapability(CAPABILITY, CapabilityClient.FILTER_ALL)
                .addOnSuccessListener(new OnSuccessListener<CapabilityInfo>() {
                    @Override
                    public void onSuccess(CapabilityInfo info) {
                        for (Node n : info.getNodes()) {
                            if (n.isNearby()) {
                                bridgeNodeId = n.getId();
                                status.setText(getString(R.string.status_connected, n.getDisplayName()));
                                return;
                            }
                        }
                        bridgeNodeId = null;
                        status.setText(R.string.status_no_bridge);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        bridgeNodeId = null;
                        status.setText(getString(R.string.status_error, e.getMessage()));
                    }
                });
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        send(CMDS[position]);
    }

    private void send(String cmd) {
        if (bridgeNodeId == null) {
            status.setText(R.string.status_no_bridge);
            findBridge();
            return;
        }
        status.setText(getString(R.string.status_sending, cmd));
        Wearable.getMessageClient(this)
                .sendMessage(bridgeNodeId, PATH_CMD, cmd.getBytes(StandardCharsets.UTF_8))
                .addOnSuccessListener(new OnSuccessListener<Integer>() {
                    @Override
                    public void onSuccess(Integer integer) {
                        status.setText(getString(R.string.status_sent, cmd));
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        status.setText(getString(R.string.status_error, e.getMessage()));
                    }
                });
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (PATH_RESULT.equals(event.getPath())) {
            String text = new String(event.getData(), StandardCharsets.UTF_8);
            status.setText(text.length() > 120 ? text.substring(0, 120) + "…" : text);
        }
    }
}
