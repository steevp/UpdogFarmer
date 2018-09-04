package com.steevsapps.idledaddy.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.listeners.GamesListUpdateListener;
import com.steevsapps.idledaddy.preferences.Prefs;
import com.steevsapps.idledaddy.steam.model.Game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public class GamesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Game> dataSet = new ArrayList<>();
    private List<Game> dataSetCopy = new ArrayList<>();
    private Context context;
    private GamePickedListener gamePickedListener;
    private ArrayList<Game> currentGames;
    private boolean headerEnabled = false;
    private Deque<List<Game>> pendingUpdates = new ArrayDeque<>();
    private boolean clearDataCopy = true;
    private GamesListUpdateListener updateListener;

    public final static int ITEM_HEADER = 1;
    public final static int ITEM_NORMAL = 2;

    public GamesAdapter(Context c) {
        context = c;
        try {
            gamePickedListener = (GamePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GamePickedListener.");
        }
    }

    public void setListener(GamesListUpdateListener listener) {
        this.updateListener = listener;
    }

    public void setData(List<Game> games) {
        pendingUpdates.push(games);
        if (pendingUpdates.size() > 1) {
            return;
        }
        updateDataInternal(games);
    }

    private void updateDataInternal(final List<Game> games) {
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new GamesDiffCallback(games, dataSet));
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        applyDiffResult(games, diffResult);
                    }
                });
            }
        }).start();
    }

    private void applyDiffResult(List<Game> games, DiffUtil.DiffResult diffResult) {
        pendingUpdates.remove(games);
        dispatchUpdates(games, diffResult);
        if (pendingUpdates.size() > 0) {
            final List<Game> latest = pendingUpdates.pop();
            pendingUpdates.clear();
            updateDataInternal(latest);
        } else {
            // Finshed
            clearDataCopy = true;
        }
    }

    private void dispatchUpdates(List<Game> games, DiffUtil.DiffResult diffResult) {
        dataSet.clear();
        dataSet.addAll(games);
        if (clearDataCopy) {
            dataSetCopy.clear();
            dataSetCopy.addAll(games);
        }
        diffResult.dispatchUpdatesTo(new GamesListUpdateCallback(this, headerEnabled));
        if (updateListener != null) {
            updateListener.onGamesListUpdated();
        }
    }

    public void filter(String text) {
        clearDataCopy = false;
        final List<Game> newGames = new ArrayList<>();
        if (text.isEmpty()) {
            newGames.addAll(dataSetCopy);
            setData(newGames);
        } else {
            for (Game game : dataSetCopy) {
                if (game.name.toLowerCase().contains(text.toLowerCase())) {
                    newGames.add(game);
                }
            }
            setData(newGames);
        }
    }

    public void setCurrentGames(ArrayList<Game> games) {
        currentGames = games;
        notifyDataSetChanged();
    }

    public void setHeaderEnabled(boolean b) {
        if (headerEnabled != b) {
            headerEnabled = b;
            if (headerEnabled) {
                notifyItemInserted(0);
            } else {
                notifyItemRemoved(0);
            }
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == ITEM_HEADER) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.games_header_item, parent, false);
            return new VHHeader(view);
        } else if (viewType == ITEM_NORMAL) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.games_item, parent, false);
            return new VHItem(view);
        }
        throw new IllegalArgumentException("Unknown view type: " + viewType);
    }

    @Override
    public int getItemViewType(int position) {
        if (headerEnabled && position == 0) {
            return ITEM_HEADER;
        }
        return ITEM_NORMAL;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == ITEM_HEADER) {
            final VHHeader header = (VHHeader) holder;
        } else if (holder.getItemViewType() == ITEM_NORMAL){
            final VHItem item = (VHItem) holder;
            final Game game = dataSet.get(headerEnabled ? position - 1 : position);
            item.name.setText(game.name);
            final int quantity = game.hoursPlayed < 1 ? 0 : (int) Math.ceil(game.hoursPlayed);
            item.hours.setText(context.getResources()
                    .getQuantityString(R.plurals.hours_on_record, quantity, game.hoursPlayed));
            if (!Prefs.minimizeData()) {
                Glide.with(context)
                        .load(game.iconUrl)
                        .into(item.logo);
            } else {
                item.logo.setImageResource(R.drawable.ic_image_white_48dp);
            }
            item.itemView.setActivated(currentGames.contains(game));
        }
    }

    @Override
    public int getItemCount() {
        if (headerEnabled) {
            return dataSet.size() + 1;
        }
        return dataSet.size();
    }

    private class VHHeader extends RecyclerView.ViewHolder implements View.OnClickListener {
        private VHHeader(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            gamePickedListener.onGamesPicked(dataSet);
            currentGames.clear();
            currentGames.addAll(dataSet);
            notifyDataSetChanged();
        }
    }

    private class VHItem extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener {
        private TextView name;
        private ImageView logo;
        private TextView hours;

        private VHItem(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            logo = itemView.findViewById(R.id.logo);
            hours = itemView.findViewById(R.id.hours);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            final int position = getAdapterPosition();
            if (position == NO_POSITION) {
                return;
            }
            final Game game = dataSet.get(headerEnabled ? position - 1 : position);
            if (!currentGames.contains(game) && currentGames.size() < 32) {
                currentGames.add(game);
                itemView.setActivated(true);
                gamePickedListener.onGamePicked(game);
            } else {
                currentGames.remove(game);
                itemView.setActivated(false);
                gamePickedListener.onGameRemoved(game);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            final int position = getAdapterPosition();
            if (position == NO_POSITION) {
                return false;
            }
            final Game game = dataSet.get(headerEnabled ? position - 1 : position);
            gamePickedListener.onGameLongPressed(game);
            return true;
        }
    }
}
