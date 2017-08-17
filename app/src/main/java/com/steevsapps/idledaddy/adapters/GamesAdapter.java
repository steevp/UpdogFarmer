package com.steevsapps.idledaddy.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GamesAdapter extends RecyclerView.Adapter<GamesAdapter.ViewHolder> {

    private List<Game> dataSet = new ArrayList<>();
    private List<Game> dataSetCopy = new ArrayList<>();
    private Context context;
    private GamePickedListener callback;
    private int currentAppId = 0;

    public GamesAdapter(Context c, List<Game> games, int appId) {
        // Sort games alphabetically
        Collections.sort(games, new Comparator<Game>() {
            @Override
            public int compare(Game game1, Game game2) {
                return game1.name.toLowerCase().compareTo(game2.name.toLowerCase());
            }
        });
        context = c;
        dataSet.addAll(games);
        dataSetCopy.addAll(games);
        currentAppId = appId;
        try {
            callback = (GamePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GamePickedListener.");
        }
    }

    public void filter(String text) {
        dataSet.clear();
        if (text.isEmpty()) {
            dataSet.addAll(dataSetCopy);
        } else {
            for (Game game : dataSetCopy) {
                if (game.name.toLowerCase().contains(text.toLowerCase())) {
                    dataSet.add(game);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setCurrentAppId(int appId) {
        if (appId != currentAppId) {
            currentAppId = appId;
            notifyDataSetChanged();
        }
    }

    public int getCurrentAppId() {
        return currentAppId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.games_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final Game game = dataSet.get(position);
        holder.name.setText(game.name);
        if (!Prefs.minimizeData()) {
            Glide.with(context)
                    .load(game.logoUrl)
                    .into(holder.logo);
        } else {
            holder.logo.setImageResource(R.drawable.ic_image_white_48dp);
        }
        if (game.appId == currentAppId) {
            holder.nowPlaying.setVisibility(View.VISIBLE);
        } else {
            holder.nowPlaying.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCurrentAppId(game.appId);
                callback.onGamePicked(game);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView name;
        private ImageView logo;
        private TextView nowPlaying;

        private ViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.name);
            logo = (ImageView) itemView.findViewById(R.id.logo);
            nowPlaying = (TextView) itemView.findViewById(R.id.now_playing);
        }
    }
}
