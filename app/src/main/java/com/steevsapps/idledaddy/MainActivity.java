package com.steevsapps.idledaddy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.Purchase;
import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.steevsapps.idledaddy.billing.BillingManager;
import com.steevsapps.idledaddy.billing.BillingUpdatesListener;
import com.steevsapps.idledaddy.dialogs.AboutDialog;
import com.steevsapps.idledaddy.dialogs.GameOptionsDialog;
import com.steevsapps.idledaddy.dialogs.RedeemDialog;
import com.steevsapps.idledaddy.fragments.DataFragment;
import com.steevsapps.idledaddy.fragments.GamesFragment;
import com.steevsapps.idledaddy.fragments.HomeFragment;
import com.steevsapps.idledaddy.fragments.SettingsFragment;
import com.steevsapps.idledaddy.listeners.DialogListener;
import com.steevsapps.idledaddy.listeners.FetchGamesListener;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.listeners.SpinnerInteractionListener;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;

import java.util.List;
import java.util.Locale;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;


public class MainActivity extends AppCompatActivity
        implements BillingUpdatesListener, DialogListener, GamePickedListener, FetchGamesListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static String DRAWER_ITEM = "DRAWER_ITEM";
    private final static String TITLE = "TITLE";
    private final static String LOGOUT_EXPANDED = "LOGOUT_EXPANDED";

    private String title;

    private boolean loggedIn = false;
    private boolean farming = false;

    // Views
    private LinearLayout mainContainer;
    private ImageView avatarView;
    private TextView usernameView;
    private DrawerLayout drawerLayout;
    private NavigationView drawerView;
    private ActionBarDrawerToggle drawerToggle;
    private ImageView logoutToggle;
    private Spinner spinnerNav;

    private ViewStub adInflater;
    private AdView adView;

    private BillingManager billingManager;

    private boolean logoutExpanded = false;
    private int drawerItemId;

    private SharedPreferences prefs;

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
            updateDrawerHeader(null);
            if (farming) {
                showDropInfo();
            }

            // Check if a Steam key was sent to us from another app
            final Intent intent = getIntent();
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                handleKeyIntent(intent);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            steamService = null;
        }
    };

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            farming = steamService.isFarming();
            if (intent.getAction().equals(SteamService.LOGIN_EVENT)) {
                final EResult result = (EResult) intent.getSerializableExtra(SteamService.RESULT);
                loggedIn = result == EResult.OK;
                updateStatus();
            } else if (intent.getAction().equals(SteamService.DISCONNECT_EVENT)) {
                loggedIn = false;
                updateStatus();
            } else if (intent.getAction().equals(SteamService.STOP_EVENT)) {
                loggedIn = steamService.isLoggedIn();
                updateStatus();
            } else if (intent.getAction().equals(SteamService.FARM_EVENT)) {
                showDropInfo();
            } else if (intent.getAction().equals(SteamService.PERSONA_EVENT)) {
                updateDrawerHeader(intent);
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
        }
        closeDrawer();
        avatarView.setImageResource(R.color.transparent);
        usernameView.setText("");
        logoutExpanded = false;
        logoutToggle.setRotation(0);
        drawerView.getMenu().setGroupVisible(R.id.logout_group, false);
        loggedIn = false;
        farming = false;
        updateStatus();
    }

    /**
     * Update drawer header with avatar and username
     */
    private void updateDrawerHeader(@Nullable Intent intent) {
        final String personaName;
        final String avatarHash;

        if (intent == null) {
            // Restore from service
            personaName = steamService.getPersonaName();
            avatarHash = steamService.getAvatarHash();
        } else {
            personaName = intent.getStringExtra(SteamService.PERSONA_NAME);
            avatarHash = intent.getStringExtra(SteamService.AVATAR_HASH);
        }

        if (!personaName.isEmpty()) {
            usernameView.setText(personaName);
        }

        if (!Prefs.minimizeData() && !avatarHash.isEmpty() && !avatarHash.equals("0000000000000000000000000000000000000000")) {
            final String avatar = String.format(Locale.US,
                    "http://cdn.akamai.steamstatic.com/steamcommunity/public/images/avatars/%s/%s_full.jpg",
                    avatarHash.substring(0, 2),
                    avatarHash);
            Glide.with(this).load(avatar).into(avatarView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mainContainer = findViewById(R.id.main_container);

        // Setup Billing Manager
        billingManager = new BillingManager(this);

        // Setup the navigation spinner (Games fragment only)
        spinnerNav = findViewById(R.id.spinner_nav);
        final ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.spinner_nav_options, R.layout.simple_spinner_title);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNav.setAdapter(adapter);
        final SpinnerInteractionListener listener = new SpinnerInteractionListener(getSupportFragmentManager());
        spinnerNav.setOnItemSelectedListener(listener);
        spinnerNav.setOnTouchListener(listener);

        drawerLayout = findViewById(R.id.drawer_layout);
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
        drawerView = findViewById(R.id.left_drawer);
        drawerView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.logout:
                        // No page for this
                        doLogout();
                        break;
                    case R.id.about:
                        // Same here
                        AboutDialog.newInstance().show(getSupportFragmentManager(), AboutDialog.TAG);
                        closeDrawer();
                        break;
                    case R.id.remove_ads:
                        billingManager.launchPurchaseFlow();
                        closeDrawer();
                        break;
                    default:
                        // Go to page
                        selectItem(item.getItemId(), true);
                        break;
                }
                return true;
            }
        });

        // Get avatar and username views from drawer header
        final View headerView = drawerView.getHeaderView(0);
        avatarView = headerView.findViewById(R.id.avatar);
        usernameView = headerView.findViewById(R.id.username);
        logoutToggle = headerView.findViewById(R.id.logout_toggle);
        headerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logoutExpanded = !logoutExpanded;
                final int rotation = logoutExpanded ? 180 : 0;
                logoutToggle.animate().rotation(rotation).setDuration(250).start();
                drawerView.getMenu().setGroupVisible(R.id.logout_group, logoutExpanded);
            }
        });

        // Update the navigation drawer and title on backstack changes
        getSupportFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                loggedIn = steamService.isLoggedIn();
                farming = steamService.isFarming();
                updateStatus();
            }
        });

        // Ads
        adInflater = findViewById(R.id.ad_inflater);

        if (savedInstanceState != null) {
            drawerItemId = savedInstanceState.getInt(DRAWER_ITEM);
            logoutExpanded = savedInstanceState.getBoolean(LOGOUT_EXPANDED);
            setTitle(savedInstanceState.getString(TITLE));
            drawerView.getMenu().setGroupVisible(R.id.logout_group, logoutExpanded);
            logoutToggle.setRotation(logoutExpanded ? 180 : 0);
        } else {
            logoutExpanded = false;
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
        outState.putBoolean(LOGOUT_EXPANDED, logoutExpanded);
    }

    /**
     * Activate a Steam key sent from another app
     */
    private void handleKeyIntent(Intent intent) {
        final String key = intent.getStringExtra(Intent.EXTRA_TEXT).trim();
        if (loggedIn) {
            steamService.redeemKey(key);
        } else {
            Toast.makeText(getApplicationContext(), R.string.error_not_logged_in, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void selectItem(int id, boolean addToBackStack) {
        if (drawerItemId == id) {
            // Already selected
            closeDrawer();
            return;
        }

        // Cleanup retained data fragment when switching screens
        final DataFragment dataFragment = (DataFragment) getSupportFragmentManager().findFragmentByTag("data");
        if (dataFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(dataFragment)
                    .commit();
        }

        Fragment fragment;
        switch (id) {
            case R.id.home:
                fragment = HomeFragment.newInstance(loggedIn, farming);
                break;
            case R.id.games:
                fragment = GamesFragment.newInstance(steamService.getSteamId(),
                        steamService.getCurrentGames(), spinnerNav.getSelectedItemPosition());
                break;
            case R.id.settings:
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
        closeDrawer();
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content_frame);
    }

    private void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(drawerView);
        }
    }

    /**
     * Show the navigation spinner (Games fragment only)
     */
    private void showSpinnerNav() {
        spinnerNav.setVisibility(View.VISIBLE);
    }

    /**
     * Hide it
     */
    private void hideSpinnerNav() {
        spinnerNav.setVisibility(View.GONE);
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
        filter.addAction(SteamService.STOP_EVENT);
        filter.addAction(SteamService.FARM_EVENT);
        filter.addAction(SteamService.PERSONA_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        startSteam();
        // Listen for preference changes
        prefs = Prefs.getPrefs();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        doUnbindService();
        prefs.unregisterOnSharedPreferenceChangeListener(this);

    }

    @Override
    protected void onDestroy() {
        billingManager.destroy();
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean loggedIn = steamService != null && steamService.isLoggedIn();
        drawerView.getHeaderView(0).setClickable(loggedIn);
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
            case R.id.redeem:
                RedeemDialog.newInstance().show(getSupportFragmentManager(), "redeem");
                break;
        }
    }

    private void startSteam() {
        ContextCompat.startForegroundService(this, SteamService.createIntent(this));
        doBindService();
    }

    private void stopSteam() {
        doUnbindService();
        stopService(SteamService.createIntent(this));
        finish();
    }

    /**
     * Update the fragments
     */
    private void updateStatus() {
        invalidateOptionsMenu();
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof HomeFragment) {
            drawerItemId = R.id.home;
            setTitle(R.string.app_name);
            hideSpinnerNav();
            drawerView.getMenu().findItem(R.id.home).setChecked(true);
            if (farming) {
                showDropInfo();
            }
            ((HomeFragment) fragment).update(loggedIn, farming);
        } else if (fragment instanceof GamesFragment) {
            drawerItemId = R.id.games;
            setTitle("");
            showSpinnerNav();
            drawerView.getMenu().findItem(R.id.games).setChecked(true);
            ((GamesFragment) fragment).update(steamService.getCurrentGames());
        } else if (fragment instanceof SettingsFragment) {
            drawerItemId = R.id.settings;
            setTitle(R.string.settings);
            hideSpinnerNav();
            drawerView.getMenu().findItem(R.id.settings).setChecked(true);
        }
    }

    /**
     * Show card drop info
     */
    private void showDropInfo() {
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof HomeFragment) {
            ((HomeFragment) fragment).showDropInfo(steamService.getGameCount(), steamService.getCardCount());
        }
    }

    /**
     * Set games list for the games fragment
     */
    private void setGames(List<Game> games) {
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof  GamesFragment) {
            ((GamesFragment) fragment).setGames(games);
        }
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
        steamService.addGame(game);
    }

    @Override
    public void onGameRemoved(Game game) {
        steamService.stopFarming();
        steamService.removeGame(game);
    }

    @Override
    public void onGameLongPressed(Game game) {
        // Show game options
        GameOptionsDialog.newInstance(game).show(getSupportFragmentManager(), GameOptionsDialog.TAG);
    }

    @Override
    public void onGamesListReceived(List<Game> games) {
        // Remove task fragment
        final Fragment taskFragment = getSupportFragmentManager().findFragmentByTag("task_fragment");
        if (taskFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(taskFragment)
                    .commitAllowingStateLoss();
        }
        // Update GamesFragment
        setGames(games);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("stay_awake")) {
            if (Prefs.stayAwake()) {
                // Don't let the screen turn off
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } else if (key.equals("offline")) {
            // Change status
            steamService.changeStatus(Prefs.getOffline() ? EPersonaState.Offline : EPersonaState.Online);
        }
    }

    @Override
    public void onBillingClientSetupFinished() {
        if (billingManager.shouldDisplayAds()) {
            loadAds();
            drawerView.getMenu().findItem(R.id.remove_ads).setVisible(true);
        }
    }

    @Override
    public void onPurchasesUpdated(List<Purchase> purchases) {
        if (!billingManager.shouldDisplayAds()) {
            removeAds();
            drawerView.getMenu().findItem(R.id.remove_ads).setVisible(false);
        }
    }

    /**
     * Inflate adView and load the ad request
     */
    private void loadAds() {
        adView = (AdView) adInflater.inflate();
        MobileAds.initialize(this, "***REMOVED***");
        final AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("0BCBCBBDA9FCA8FE47AEA0C5D1BCBE99")
                .addTestDevice("E8F66CC8599C1F21FDBC86370F926F88")
                .build();
        adView.loadAd(adRequest);
    }

    /**
     * Remove the adView
     */
    private void removeAds() {
        mainContainer.removeView(adView);
    }
}
