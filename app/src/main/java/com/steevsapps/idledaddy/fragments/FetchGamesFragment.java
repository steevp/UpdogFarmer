package com.steevsapps.idledaddy.fragments;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.steevsapps.idledaddy.listeners.FetchGamesListener;
import com.steevsapps.idledaddy.steam.WebScraper;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

public class FetchGamesFragment extends Fragment {
    private final static String TAG = FetchGamesFragment.class.getSimpleName();
    private final static String STEAM_ID = "STEAM_ID";

    private long steamId;
    private FetchGamesListener callback;

    public static FetchGamesFragment newInstance(long steamId) {
        final FetchGamesFragment fragment = new FetchGamesFragment();
        final Bundle args = new Bundle();
        args.putLong(STEAM_ID, steamId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (FetchGamesListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement FetchGamesListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        steamId = getArguments().getLong(STEAM_ID);
        new FetchGamesTask().execute();
    }

    private class FetchGamesTask extends AsyncTask<Void,Void,List<Game>> {
        @Override
        protected List<Game> doInBackground(Void... voids) {
            return WebScraper.getGamesOwned(steamId);
        }

        @Override
        protected void onPostExecute(List<Game> games) {
            // Add the Steam client ¯\_(ツ)_/¯
            final Game steamClient = new Game(753, "Steam", 0, 0);
            steamClient.iconUrl = "http://cdn.akamai.steamstatic.com/steamcommunity/public/images/apps/753/ebc73b4e326945ea7eb986d93e2b1aabb291fe7d.jpg";
            games.add(steamClient);

            if (callback != null) {
                callback.onGamesListReceived(games);
            }
        }
    }

}
