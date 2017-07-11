package com.steevsapps.updogfarmer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.steevsapps.updogfarmer.steam.SteamService;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = "ywtag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
            case R.id.start_service:
                startSteam();
                break;
            case R.id.stop_service:
                stopSteam();
                break;
        }
    }

    private void startSteam() {
        final Intent intent = new Intent(this, SteamService.class);
        startService(intent);
    }

    private void stopSteam() {
        final Intent intent = new Intent(this, SteamService.class);
        stopService(intent);
    }
}
