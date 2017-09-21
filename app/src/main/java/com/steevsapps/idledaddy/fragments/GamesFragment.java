package com.steevsapps.idledaddy.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.adapters.GamesAdapter;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

public class GamesFragment extends Fragment implements SearchView.OnQueryTextListener {
    private final static String TAG = GamesFragment.class.getSimpleName();
    private final static String STEAM_ID = "STEAM_ID";
    private final static String CURRENT_APPID = "CURRENT_APPID";

    private RecyclerView recyclerView;
    private GamesAdapter adapter;
    private GridLayoutManager layoutManager;
    private SearchView searchView;
    private TextView emptyView;
    private ProgressBar progressBar;
    private DataFragment dataFragment;
    private FloatingActionButton fab;

    private long steamId;
    private int currentAppId;

    public static GamesFragment newInstance(long steamId, int currentAppId) {
        final GamesFragment fragment = new GamesFragment();
        final Bundle args = new Bundle();
        args.putLong(STEAM_ID, steamId);
        args.putInt(CURRENT_APPID, currentAppId);
        fragment.setArguments(args);
        return fragment;
    }

    public void update(int appId) {
        currentAppId = appId;
        adapter.setCurrentAppId(appId);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        steamId = getArguments().getLong(STEAM_ID);
        if (savedInstanceState != null) {
            currentAppId = savedInstanceState.getInt(CURRENT_APPID);
        } else {
            currentAppId = getArguments().getInt(CURRENT_APPID);
            if (steamId == 0) {
                Toast.makeText(getActivity(), R.string.error_not_logged_in, Toast.LENGTH_LONG).show();
            }
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_APPID, adapter.getCurrentAppId());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.games_fragment, container, false);
        recyclerView = view.findViewById(R.id.games_list);
        layoutManager = new GridLayoutManager(recyclerView.getContext(), getResources().getInteger(R.integer.game_columns));
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        adapter = new GamesAdapter(recyclerView.getContext());
        adapter.setCurrentAppId(currentAppId);
        recyclerView.setAdapter(adapter);
        emptyView = view.findViewById(R.id.empty_view);
        progressBar = view.findViewById(R.id.progress);
        fab = view.findViewById(R.id.redeem);
        // Show redeem button if user is logged in
        if (steamId > 0) {
            fab.show();
        }
        dataFragment = (DataFragment) getActivity().getSupportFragmentManager().findFragmentByTag("data");
        if (dataFragment != null) {
            // Restore games list
            Log.i(TAG, "Restoring " + currentAppId);
            updateGames(dataFragment.getData());
        } else {
            // Fetch games list
            dataFragment = new DataFragment();
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .add(dataFragment, "data")
                    .commit();
            final FetchGamesFragment taskFragment = FetchGamesFragment.newInstance(steamId);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .add(taskFragment, "task_fragment")
                    .commit();
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

    /**
     * Update games list
     * @param games the list of games
     */
    public void updateGames(List<Game> games) {
        dataFragment.setData(games);
        adapter.setData(games);
        progressBar.setVisibility(View.GONE);
        if (games.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        }
    }
}
