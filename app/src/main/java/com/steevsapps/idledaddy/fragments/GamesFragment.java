package com.steevsapps.idledaddy.fragments;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.adapters.GamesAdapter;
import com.steevsapps.idledaddy.listeners.GamesListUpdateListener;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

public class GamesFragment extends Fragment
        implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener, GamesListUpdateListener {
    private final static String TAG = GamesFragment.class.getSimpleName();
    private final static String STEAM_ID = "STEAM_ID";
    private final static String CURRENT_GAMES = "CURRENT_GAMES";
    private final static String CURRENT_TAB = "CURRENT_TAB";

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private GamesAdapter adapter;
    private GridLayoutManager layoutManager;
    private SearchView searchView;
    private TextView emptyView;
    private GamesViewModel viewModel;
    private FloatingActionButton fab;

    private long steamId;
    private ArrayList<Game> currentGames;

    // Spinner nav items
    public final static int TAB_GAMES = 0;
    public final static int TAB_LAST = 1;
    public final static int TAB_BLACKLIST = 2;
    private int currentTab = TAB_GAMES;

    public static GamesFragment newInstance(long steamId, ArrayList<Game> currentGames, int position) {
        final GamesFragment fragment = new GamesFragment();
        final Bundle args = new Bundle();
        args.putLong(STEAM_ID, steamId);
        args.putParcelableArrayList(CURRENT_GAMES, currentGames);
        args.putInt(CURRENT_TAB, position);
        fragment.setArguments(args);
        return fragment;
    }

    public void update(ArrayList<Game> games) {
        currentGames = games;
        adapter.setCurrentGames(currentGames);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        steamId = getArguments().getLong(STEAM_ID);
        viewModel = ViewModelProviders.of(this).get(GamesViewModel.class);
        viewModel.init(SteamWebHandler.getInstance(), steamId);
        if (savedInstanceState != null) {
            currentGames = savedInstanceState.getParcelableArrayList(CURRENT_GAMES);
            currentTab = savedInstanceState.getInt(CURRENT_TAB);
        } else {
            currentGames = getArguments().getParcelableArrayList(CURRENT_GAMES);
            currentTab = getArguments().getInt(CURRENT_TAB);
            if (steamId == 0) {
                Toast.makeText(getActivity(), R.string.error_not_logged_in, Toast.LENGTH_LONG).show();
            }
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        if (!currentGames.isEmpty()) {
            // Save idling session
            PrefsManager.writeLastSession(currentGames);
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(CURRENT_GAMES, currentGames);
        outState.putInt(CURRENT_TAB, currentTab);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.games_fragment, container, false);
        refreshLayout = view.findViewById(R.id.refresh_layout);
        refreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark);
        refreshLayout.setOnRefreshListener(this);
        refreshLayout.setRefreshing(true);

        recyclerView = view.findViewById(R.id.games_list);
        adapter = new GamesAdapter(recyclerView.getContext());
        adapter.setListener(this);
        adapter.setCurrentGames(currentGames);
        adapter.setHeaderEnabled(currentTab == TAB_LAST);
        layoutManager = new GridLayoutManager(recyclerView.getContext(), getResources().getInteger(R.integer.game_columns));
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (adapter.getItemViewType(position)) {
                    case GamesAdapter.ITEM_HEADER:
                        return layoutManager.getSpanCount();
                    case GamesAdapter.ITEM_NORMAL:
                        return 1;
                    default:
                        return -1;
                }
            }
        });
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        emptyView = view.findViewById(R.id.empty_view);
        fab = view.findViewById(R.id.redeem);
        // Show redeem button if user is logged in
        if (steamId > 0) {
            fab.show();
        }

        viewModel.getGames().observe(this, new Observer<List<Game>>() {
            @Override
            public void onChanged(@Nullable List<Game> games) {
                setGames(games);
            }
        });
        loadData();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_games, menu);
        final MenuItem searchItem = menu.findItem(R.id.search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(this);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                fetchGames();
                return true;
            case R.id.sort_alphabetically:
                viewModel.sortAlphabetically();
                return true;
            case R.id.sort_hours_played:
                viewModel.sortHoursPlayed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        adapter.filter(query);
        searchView.clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        adapter.filter(newText);
        return true;
    }

    private void loadData() {
        if (viewModel.getGames().getValue() == null) {
            fetchGames();
        }
    }

    /**
     * Switch to the 'Games' tab
     */
    public void switchToGames() {
        currentTab = TAB_GAMES;
        fetchGames();
    }

    /**
     * Switch to the 'Last Session' tab
     */
    public void switchToLastSession() {
        currentTab = TAB_LAST;
        fetchGames();
    }

    /**
     * Switch to the 'Blacklist' tab
     */
    public void switchToBlacklist() {
        currentTab = TAB_BLACKLIST;
        fetchGames();
    }

    private void fetchGames() {
        if (currentTab == TAB_LAST) {
            // Load last idling session
            final List<Game> games = !currentGames.isEmpty() ? currentGames : PrefsManager.getLastSession();
            viewModel.setGames(games);
        } else {
            // Fetch games from Steam
            refreshLayout.setRefreshing(true);
            viewModel.fetchGames();
        }
    }

    /**
     * Update games list
     * @param games the list of games
     */
    private void setGames(List<Game> games) {
        if (currentTab == TAB_BLACKLIST) {
            // Only list blacklisted games
            final List<String> blacklist = PrefsManager.getBlacklist();
            final List<Game> blacklistGames = new ArrayList<>();
            for (Game game : games) {
                if (blacklist.contains(String.valueOf(game.appId))) {
                    blacklistGames.add(game);
                }
            }
            adapter.setHeaderEnabled(false);
            adapter.setData(blacklistGames);
            emptyView.setVisibility(blacklistGames.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            adapter.setHeaderEnabled(!games.isEmpty() && currentTab == TAB_LAST);
            adapter.setData(games);
            emptyView.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onRefresh() {
        fetchGames();
    }

    @Override
    public void onGamesListUpdated() {
        // Scroll to top
        recyclerView.scrollToPosition(0);
        refreshLayout.setRefreshing(false);
    }
}
