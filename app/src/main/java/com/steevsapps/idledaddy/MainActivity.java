package com.steevsapps.idledaddy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.steevsapps.idledaddy.dialogs.RedeemDialog;
import com.steevsapps.idledaddy.fragments.DataFragment;
import com.steevsapps.idledaddy.fragments.GamesFragment;
import com.steevsapps.idledaddy.fragments.HomeFragment;
import com.steevsapps.idledaddy.fragments.SettingsFragment;
import com.steevsapps.idledaddy.listeners.DialogListener;
import com.steevsapps.idledaddy.listeners.FetchGamesListener;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;

import java.util.List;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;


public class MainActivity extends AppCompatActivity implements DialogListener, GamePickedListener, FetchGamesListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    public final static String UPDATE_STATUS = "UPDATE_STATUS";
    public final static String STATUS = "STATUS";
    public final static String FARMING = "FARMING";

    private final static String DRAWER_ITEM = "DRAWER_ITEM";
    private final static String TITLE = "TITLE";

    private String title;

    private boolean loggedIn = false;
    private boolean farming = false;

    // Views
    private DrawerLayout drawerLayout;
    private NavigationView drawerView;
    private ActionBarDrawerToggle drawerToggle;
    private int drawerItemId;

    // Service connection
    private boolean isBound;
    private SteamService steamService;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service connected");
            steamService = ((SteamService.LocalBinder) service).getService();
            loggedIn = steamService.isLoggedIn();
            farming = steamService.isFarming();
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
            if (intent.getAction().equals(SteamService.LOGIN_EVENT)) {
                final EResult result = (EResult) intent.getSerializableExtra(SteamService.RESULT);
                loggedIn = result == EResult.OK;
            } else if (intent.getAction().equals(SteamService.DISCONNECT_EVENT)) {
                loggedIn = false;
            }
            farming = steamService.isFarming();
            updateStatus();
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
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        // On tablets we use the DrawerView but not the DrawerLayout
        if (drawerLayout != null) {
            drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer) {
                @Override
                public void onDrawerClosed(View drawerView) {
                    super.onDrawerClosed(drawerView);
                    invalidateOptionsMenu();
                }

                @Override
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    invalidateOptionsMenu();
                }
            };
            drawerLayout.addDrawerListener(drawerToggle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
        drawerView = (NavigationView) findViewById(R.id.left_drawer);
        drawerView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                selectItem(item.getItemId(), true);
                return true;
            }
        });

        // Update the navigation drawer and title on backstack changes
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                loggedIn = steamService.isLoggedIn();
                farming = steamService.isFarming();
                updateStatus();
                final Fragment fragment = getCurrentFragment();
                if (fragment instanceof HomeFragment) {
                    drawerItemId = R.id.home;
                    setTitle(R.string.app_name);
                    drawerView.getMenu().findItem(R.id.home).setChecked(true);
                } else if (fragment instanceof GamesFragment) {
                    drawerItemId = R.id.games;
                    setTitle(R.string.games);
                    drawerView.getMenu().findItem(R.id.games).setChecked(true);
                } else if (fragment instanceof SettingsFragment) {
                    drawerItemId = R.id.settings;
                    setTitle(R.string.settings);
                    drawerView.getMenu().findItem(R.id.settings).setChecked(true);
                }
            }
        });

        if (savedInstanceState != null) {
            drawerItemId = savedInstanceState.getInt(DRAWER_ITEM);
            setTitle(savedInstanceState.getString(TITLE));
        } else {
            selectItem(R.id.home, false);
        }

        applySettings();
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null) {
            drawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(DRAWER_ITEM, drawerItemId);
        outState.putString(TITLE, title);
    }

    private void selectItem(int id, boolean addToBackStack) {
        if (drawerItemId == id) {
            // Already selected
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(drawerView);
            }
            return;
        }

        // Cleanup retained data fragment when switching screens
        final DataFragment dataFragment = (DataFragment) getSupportFragmentManager().findFragmentByTag("data");
        if (dataFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(dataFragment)
                    .commit();
        }

        drawerItemId = id;
        drawerView.getMenu().findItem(id).setChecked(true);
        Fragment fragment;
        switch (id) {
            case R.id.home:
                setTitle(R.string.app_name);
                fragment = HomeFragment.newInstance(loggedIn);
                break;
            case R.id.games:
                setTitle(R.string.games);
                fragment = GamesFragment.newInstance(steamService.getSteamId(), steamService.getCurrentAppId());
                break;
            case R.id.settings:
                setTitle(R.string.settings);
                fragment = SettingsFragment.newInstance();
                break;
            default:
                fragment = new Fragment();
                break;
        }
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content_frame, fragment);
        if (addToBackStack) {
            ft.addToBackStack(null);
        }
        ft.commit();
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(drawerView);
        }
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content_frame);
    }

    private void applySettings() {
        if (Prefs.stayAwake()) {
            // Don't let the screen turn off
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void setTitle(int titleId) {
        title = getString(titleId);
        super.setTitle(titleId);
    }

    @Override
    public void setTitle(CharSequence title) {
        this.title = title.toString();
        super.setTitle(title);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SteamService.LOGIN_EVENT);
        filter.addAction(SteamService.DISCONNECT_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        startSteam();
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
        final MenuItem redeem = menu.findItem(R.id.redeem);
        final boolean loggedIn = steamService != null && steamService.isLoggedIn();
        logOff.setVisible(loggedIn);
        redeem.setVisible(loggedIn);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.redeem:
                RedeemDialog.newInstance().show(getSupportFragmentManager(), "redeem");
                return true;
            case R.id.logout:
                doLogout();
                return true;
        }
        return false;
    }

    public void clickHandler(View v) {
        switch (v.getId()) {
            case R.id.start_idling:
                v.setEnabled(false);
                steamService.startFarming();
                break;
            case R.id.stop_idling:
                stopSteam();
                break;
            case R.id.status:
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

    private void updateStatus() {
        final Intent intent = new Intent(UPDATE_STATUS);
        intent.putExtra(STATUS, loggedIn);
        intent.putExtra(FARMING, farming);
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(intent);
        invalidateOptionsMenu();
    }

    @Override
    public void onYesPicked(String text) {
        final String key = text.toUpperCase().trim();
        if (!key.isEmpty()) {
            steamService.redeemKey(key);
        }
    }

    @Override
    public void onGamePicked(Game game) {
        steamService.stopFarming();
        steamService.idleSingle(game);
    }

    @Override
    public void onGamesListReceived(List<Game> games) {
        // Remove task fragment
        final Fragment taskFragment = getSupportFragmentManager().findFragmentByTag("task_fragment");
        if (taskFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(taskFragment)
                    .commit();
        }
        // Update GamesFragment
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof GamesFragment) {
            ((GamesFragment) fragment).update(games);
        }
    }
}
