package com.steevsapps.idledaddy.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
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
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

public class GamesFragment extends Fragment implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    private final static String TAG = GamesFragment.class.getSimpleName();
    private final static String STEAM_ID = "STEAM_ID";
    private final static String CURRENT_APPIDS = "CURRENT_APPIDS";

    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private GamesAdapter adapter;
    private GridLayoutManager layoutManager;
    private SearchView searchView;
    private TextView emptyView;
    private DataFragment dataFragment;
    private FloatingActionButton fab;

    private long steamId;
    private ArrayList<Integer> currentAppIds;

    public static GamesFragment newInstance(long steamId, ArrayList<Integer> currentAppIds) {
        final GamesFragment fragment = new GamesFragment();
        final Bundle args = new Bundle();
        args.putLong(STEAM_ID, steamId);
        args.putIntegerArrayList(CURRENT_APPIDS, currentAppIds);
        fragment.setArguments(args);
        return fragment;
    }

    public void update(ArrayList<Integer> appIds) {
        currentAppIds = appIds;
        adapter.setCurrentAppIds(appIds);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        steamId = getArguments().getLong(STEAM_ID);
        if (savedInstanceState != null) {
            currentAppIds = savedInstanceState.getIntegerArrayList(CURRENT_APPIDS);
        } else {
            currentAppIds = getArguments().getIntegerArrayList(CURRENT_APPIDS);
            if (steamId == 0) {
                Toast.makeText(getActivity(), R.string.error_not_logged_in, Toast.LENGTH_LONG).show();
            }
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntegerArrayList(CURRENT_APPIDS, currentAppIds);
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
        layoutManager = new GridLayoutManager(recyclerView.getContext(), getResources().getInteger(R.integer.game_columns));
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        adapter = new GamesAdapter(recyclerView.getContext());
        adapter.setCurrentAppIds(currentAppIds);
        recyclerView.setAdapter(adapter);

        emptyView = view.findViewById(R.id.empty_view);
        fab = view.findViewById(R.id.redeem);
        // Show redeem button if user is logged in
        if (steamId > 0) {
            fab.show();
        }
        dataFragment = getDataFragment();
        if (dataFragment != null) {
            // Restore games list
            Log.i(TAG, "Restoring games list");
            updateGames(dataFragment.getData());
        } else {
            // Add data fragment to store games during configuration changes
            dataFragment = new DataFragment();
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .add(dataFragment, "data")
                    .commit();
            // Fetch games list
            fetchGames();
        }
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
        if (item.getItemId() == R.id.refresh) {
            doRefresh();
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

    @Nullable
    private DataFragment getDataFragment() {
        return (DataFragment) getActivity().getSupportFragmentManager().findFragmentByTag("data");
    }

    /**
     * Fetch the games list from Steam
     */
    private void fetchGames() {
        final FetchGamesFragment taskFragment = FetchGamesFragment.newInstance(steamId);
        getActivity().getSupportFragmentManager()
                .beginTransaction()
                .add(taskFragment, "task_fragment")
                .commit();
    }

    /**
     * Update games list
     * @param games the list of games
     */
    public void updateGames(List<Game> games) {
        dataFragment.setData(games);
        adapter.setData(games);
        refreshLayout.setRefreshing(false);
        if (games.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRefresh() {
        doRefresh();
    }

    public void doRefresh() {
        refreshLayout.setRefreshing(true);
        fetchGames();
    }
}
