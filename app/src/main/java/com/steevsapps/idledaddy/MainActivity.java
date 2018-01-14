package com.steevsapps.idledaddy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
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
import com.steevsapps.idledaddy.base.BaseActivity;
import com.steevsapps.idledaddy.billing.BillingManager;
import com.steevsapps.idledaddy.billing.BillingUpdatesListener;
import com.steevsapps.idledaddy.dialogs.AboutDialog;
import com.steevsapps.idledaddy.dialogs.AutoDiscoverDialog;
import com.steevsapps.idledaddy.dialogs.GameOptionsDialog;
import com.steevsapps.idledaddy.dialogs.RedeemDialog;
import com.steevsapps.idledaddy.fragments.GamesFragment;
import com.steevsapps.idledaddy.fragments.HomeFragment;
import com.steevsapps.idledaddy.fragments.SettingsFragment;
import com.steevsapps.idledaddy.listeners.DialogListener;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.listeners.SpinnerInteractionListener;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;


public class MainActivity extends BaseActivity implements BillingUpdatesListener, DialogListener,
        GamePickedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static String DRAWER_ITEM = "DRAWER_ITEM";
    private final static String TITLE = "TITLE";
    private final static String LOGOUT_EXPANDED = "LOGOUT_EXPANDED";

    private String title = "";
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
    private SearchView searchView;
    private ViewStub adInflater;
    private AdView adView;

    private BillingManager billingManager;

    private boolean logoutExpanded = false;
    private int drawerItemId;

    private SharedPreferences prefs;
    private SteamService steamService;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loggedIn = steamService.isLoggedIn();
            farming = steamService.isFarming();
            switch (intent.getAction()) {
                case SteamService.LOGIN_EVENT:
                case SteamService.DISCONNECT_EVENT:
                case SteamService.STOP_EVENT:
                    updateStatus();
                    break;
                case SteamService.FARM_EVENT:
                    showDropInfo(intent);
                    break;
                case SteamService.PERSONA_EVENT:
                    updateDrawerHeader(intent);
                    break;
                case SteamService.NOW_PLAYING_EVENT:
                    showNowPlaying();
                    break;
            }
        }
    };

    private void doLogout() {
        steamService.logoff();
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

        if (intent != null) {
            personaName = intent.getStringExtra(SteamService.PERSONA_NAME);
            avatarHash = intent.getStringExtra(SteamService.AVATAR_HASH);
            PrefsManager.writePersonaName(personaName);
            PrefsManager.writeAvatarHash(avatarHash);
        } else {
            personaName = PrefsManager.getPersonaName();
            avatarHash = PrefsManager.getAvatarHash();
        }

        if (!personaName.isEmpty()) {
            usernameView.setText(personaName);
        }

        if (!PrefsManager.minimizeData() && !avatarHash.isEmpty() && !avatarHash.equals("0000000000000000000000000000000000000000")) {
            final String avatar = String.format(Locale.US,
                    "http://cdn.akamai.steamstatic.com/steamcommunity/public/images/avatars/%s/%s_full.jpg",
                    avatarHash.substring(0, 2),
                    avatarHash);
            Glide.with(this).load(avatar).into(avatarView);
        }
    }

    @Override
    protected void onServiceConnected() {
        Log.i(TAG, "Service connected");
        steamService = getService();
        loggedIn = steamService.isLoggedIn();
        farming = steamService.isFarming();
        updateStatus();
        updateDrawerHeader(null);

        // Check if a Steam key was sent to us from another app
        final Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            handleKeyIntent(intent);
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
        final String key = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (loggedIn && key != null) {
            steamService.redeemKey(key.trim());
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
        if (PrefsManager.stayAwake()) {
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
        filter.addAction(SteamService.NOW_PLAYING_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
        // Listen for preference changes
        prefs = PrefsManager.getPrefs();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
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
        menu.findItem(R.id.auto_discovery).setVisible(loggedIn);
        menu.findItem(R.id.auto_vote).setVisible(loggedIn);
        menu.findItem(R.id.search).setVisible(drawerItemId == R.id.games);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!searchView.isIconified()) {
            // Dismiss the SearchView
            searchView.setQuery("", false);
            searchView.setIconified(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.logcat:
                sendLogcat();
                return true;
            case R.id.auto_discovery:
                AutoDiscoverDialog.newInstance().show(getSupportFragmentManager(), AutoDiscoverDialog.TAG);
                return true;
            case R.id.auto_vote:
                steamService.autoVote();
                return true;
        }
        return false;
    }

    /**
     * Send Logcat output via email
     */
    private void sendLogcat() {
        final File cacheDir = getExternalCacheDir();
        if (cacheDir == null) {
            Log.i(TAG, "Unable to save Logcat. Shared storage is unavailable!");
            return;
        }
        final File file = new File(cacheDir, "idledaddy-logcat.txt");
        try {
            Utils.saveLogcat(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"steevsapps@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Idle Daddy Logcat");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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
            case R.id.stop_button:
                steamService.stopGame();
                break;
            case R.id.pause_resume_button:
                if (steamService.isPaused()) {
                    steamService.resumeGame();
                } else {
                    steamService.pauseGame();
                }
                break;
            case R.id.next_button:
                steamService.skipGame();
                break;
        }
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
            ((HomeFragment) fragment).update(loggedIn, farming);
            showDropInfo(null);
            showNowPlaying();
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
     * Show/hide card drop info
     */
    private void showDropInfo(@Nullable Intent intent) {
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof HomeFragment) {
            final HomeFragment homeFragment = (HomeFragment) fragment;
            if (intent != null) {
                // Called by FARM_EVENT, always show drop info
                final int gameCount = intent.getIntExtra(SteamService.GAME_COUNT, 0);
                final int cardCount = intent.getIntExtra(SteamService.CARD_COUNT, 0);
                homeFragment.showDropInfo(gameCount, cardCount);
            } else if (farming) {
                // Called by updateStatus(), only show drop info if we're farming
                homeFragment.showDropInfo(steamService.getGameCount(), steamService.getCardCount());
            } else {
                // Hide drop info
                homeFragment.hideDropInfo();
            }
        }
    }

    /**
     * Show now playing if we're idling any games
     */
    private void showNowPlaying() {
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof HomeFragment) {
            ((HomeFragment) fragment).showNowPlaying(steamService.getCurrentGames(), steamService.isFarming(), steamService.isPaused());
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
        steamService.addGame(game);
    }

    @Override
    public void onGamesPicked(List<Game> games) {
        steamService.addGames(games);
    }

    @Override
    public void onGameRemoved(Game game) {
        steamService.removeGame(game);
    }

    @Override
    public void onGameLongPressed(Game game) {
        // Show game options
        GameOptionsDialog.newInstance(game).show(getSupportFragmentManager(), GameOptionsDialog.TAG);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("stay_awake")) {
            if (PrefsManager.stayAwake()) {
                // Keep device awake
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                steamService.acquireWakeLock();
            } else {
                // Allow device to sleep
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                steamService.releaseWakeLock();
            }
        } else if (key.equals("offline")) {
            // Change status
            steamService.changeStatus(PrefsManager.getOffline() ? EPersonaState.Offline : EPersonaState.Online);
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
        if (adView == null) {
            adView = (AdView) adInflater.inflate();
        }
        MobileAds.initialize(this, "ca-app-pub-6413501894389361~6190763130");
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
