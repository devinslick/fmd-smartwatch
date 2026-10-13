package com.devinslick.fmdwearbridge;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Minimal settings: FMD trigger word + notification password. The watch app
 * holds no credentials; everything is composed phone-side.
 */
public class MainActivity extends Activity {

    private static final int REQ_NOTIFS = 1;

    private EditText trigger;
    private EditText pin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trigger = (EditText) findViewById(R.id.trigger);
        pin = (EditText) findViewById(R.id.pin);
        trigger.setText(Prefs.getTrigger(this));
        pin.setText(Prefs.getPin(this));

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.get(MainActivity.this).edit()
                        .putString(Prefs.TRIGGER, trigger.getText().toString().trim())
                        .putString(Prefs.PIN, pin.getText().toString().trim())
                        .apply();
                Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFS);
        }
    }
}
