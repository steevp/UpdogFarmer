package com.steevsapps.updogfarmer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.steevsapps.updogfarmer.steam.SteamService;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = "ywtag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            // Start Steam Service
            final Intent serviceIntent = new Intent(this, SteamService.class);
            startService(serviceIntent);
        }
    }
}
