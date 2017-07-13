package com.steevsapps.updogfarmer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.steevsapps.updogfarmer.steam.SteamService;
import com.steevsapps.updogfarmer.utils.Prefs;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = "ywtag";

    // Status messages
    private View statusLoggedIn;
    private View statusLoggedOff;

    // Buttons
    private Button startIdling;
    //private Button idleToReady;
    private Button stopIdling;

    // Service connection
    private boolean isBound;
    private SteamService steamService;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            steamService = ((SteamService.LocalBinder) service).getService();
            Log.i(TAG, "Service connected");
            updateStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            steamService = null;
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SteamService.LOGIN_INTENT)) {
                updateStatus();
            }
        }
    };

    private void doBindService() {
        if (!isBound) {
            Log.i(TAG, "binding service");
            bindService(new Intent(MainActivity.this, SteamService.class),
                    connection, Context.BIND_AUTO_CREATE);
            isBound = true;
        }
    }

    private void doUnbindService() {
        if (isBound) {
            Log.i(TAG, "unbinding service");
            // Detach our existing connection
            unbindService(connection);
            isBound = false;
        }
    }

    private void doLogout() {
        if (steamService != null) {
            steamService.logoff();
            startActivity(LoginActivity.createIntent(this));
        }
    }

    /**
     * Update status message
     */
    private void updateStatus() {
        statusLoggedIn.setVisibility(View.GONE);
        statusLoggedOff.setVisibility(View.GONE);
        startIdling.setEnabled(false);
        //idleToReady.setEnabled(false);
        if (steamService != null && steamService.isLoggedIn()) {
            statusLoggedIn.setVisibility(View.VISIBLE);
            startIdling.setEnabled(true);
            //idleToReady.setEnabled(true);
        } else {
            statusLoggedOff.setVisibility(View.VISIBLE);
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusLoggedIn = findViewById(R.id.status_logged_in);
        statusLoggedOff = findViewById(R.id.status_not_logged_in);
        startIdling = (Button) findViewById(R.id.start_idling);
        //idleToReady = (Button) findViewById(R.id.idle_to_ready);
        stopIdling = (Button) findViewById(R.id.stop_idling);

        if (savedInstanceState == null) {
            startSteam();
        }

        if (Prefs.stayAwake()) {
            // Don't let the screen turn off
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter(SteamService.LOGIN_INTENT));
        doBindService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        doUnbindService();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem logOff = menu.findItem(R.id.logout);
        logOff.setVisible(steamService != null && steamService.isLoggedIn());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(SettingsActivity.createIntent(this));
                return true;
            case R.id.logout:
                doLogout();
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
            case R.id.start_idling:
                steamService.startFarming();
                break;
            case R.id.stop_idling:
                stopSteam();
                break;
            case R.id.status_not_logged_in:
                startActivity(LoginActivity.createIntent(this));
                break;
        }
    }

    private void startSteam() {
        startService(SteamService.createIntent(this));
        doBindService();
    }

    private void stopSteam() {
        doUnbindService();
        stopService(SteamService.createIntent(this));
        finish();
    }
}
