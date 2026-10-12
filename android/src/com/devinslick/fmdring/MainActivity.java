package com.devinslick.fmdring;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText mTrigger;
    private EditText mPin;
    private CheckBox mDebug;
    private TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.ensureChannels(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setText("FMD Ring");
        root.addView(title);

        TextView lblTrigger = label("FMD trigger word");
        root.addView(lblTrigger);
        mTrigger = new EditText(this);
        mTrigger.setSingleLine();
        mTrigger.setText(Prefs.getTrigger(this));
        root.addView(mTrigger);

        TextView lblPin = label("Notification password (FMD Access Control)");
        root.addView(lblPin);
        mPin = new EditText(this);
        mPin.setSingleLine();
        mPin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        mPin.setText(Prefs.getPin(this));
        root.addView(mPin);

        mDebug = new CheckBox(this);
        mDebug.setText("PK2 diagnostic notifications");
        mDebug.setChecked(Prefs.getDebug(this));
        root.addView(mDebug);

        Button bSave = new Button(this);
        bSave.setText("Save");
        bSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String trigger = mTrigger.getText().toString().trim();
                if (trigger.isEmpty()) {
                    trigger = "fmd";
                }
                Prefs.get(MainActivity.this).edit()
                        .putString(Prefs.TRIGGER, trigger)
                        .putString(Prefs.PIN, mPin.getText().toString().trim())
                        .putBoolean(Prefs.DEBUG, mDebug.isChecked())
                        .apply();
                refreshStatus();
                Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(bSave);

        mStatus = new TextView(this);
        mStatus.setTextSize(13);
        mStatus.setPadding(0, 32, 0, 32);
        root.addView(mStatus);

        Button bRepost = new Button(this);
        bRepost.setText("Repost watch notification");
        bRepost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Notifier.postTriggerNotification(MainActivity.this);
                Toast.makeText(MainActivity.this, "Posted", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(bRepost);

        Button bRing = new Button(this);
        bRing.setText("Ring now (test)");
        bRing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Notifier.postCommandNotification(MainActivity.this, Notifier.ringCommand(MainActivity.this));
            }
        });
        root.addView(bRing);

        TextView help = new TextView(this);
        help.setTextSize(12);
        help.setPadding(0, 32, 0, 0);
        help.setText("Fire from anything:\n"
                + "am broadcast -a " + RingReceiver.ACTION_RING + "\n"
                + "optional: --es command \"locate\"\n\n"
                + "Or select the \"Find phone\" notification on your Pebble.");
        root.addView(help);

        setContentView(scroll);
        refreshStatus();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        Notifier.postTriggerNotification(this);
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(0, 24, 0, 4);
        return tv;
    }

    private void refreshStatus() {
        String pin = Prefs.getPin(this);
        mStatus.setText("Current command:\n"
                + Notifier.ringCommand(this)
                + (pin.isEmpty() ? "\n\n(warning: no password set - FMD will deny)" : ""));
    }
}
