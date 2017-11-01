package com.steevsapps.idledaddy.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.steevsapps.idledaddy.steam.SteamService;

/**
 * Base activity that's bound to the Steam Service
 */
public abstract class BaseActivity extends AppCompatActivity {
    private final static String TAG = BaseActivity.class.getSimpleName();

    private boolean serviceBound = false;
    private SteamService service;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            service = ((SteamService.LocalBinder) iBinder).getService();
            BaseActivity.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service = null;
            serviceBound = false;
        }
    };

    /**
     * Get the Steam Service
     */
    protected SteamService getService() {
        return service;
    }

    /**
     * Executed when the Activity is connected to the Service
     */
    protected void onServiceConnected() {
    }

    @Override
    protected void onPause() {
        super.onPause();
        doUnbind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        doBind();
    }

    /**
     * Bind Activity to the service
     */
    private void doBind() {
        Log.i(TAG, "Binding service...");
        final Intent serviceIntent = SteamService.createIntent(this);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        serviceBound = true;
    }

    /**
     * Unbind Activity from the service
     */
    private void doUnbind() {
        if (serviceBound) {
            Log.i(TAG, "Unbinding service...");
            unbindService(connection);
            serviceBound = false;
        }
    }

    /**
     * Stop Steam Service and finish Activity
     */
    protected void stopSteam() {
        doUnbind();
        stopService(SteamService.createIntent(this));
        finish();
    }
}
