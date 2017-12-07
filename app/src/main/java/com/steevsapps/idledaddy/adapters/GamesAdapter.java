package com.steevsapps.idledaddy.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GamesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static int SORT_ALPHABETICALLY = 1;
    public static int SORT_HOURS_PLAYED = 2;

    private List<Game> dataSet = new ArrayList<>();
    private List<Game> dataSetCopy = new ArrayList<>();
    private Context context;
    private GamePickedListener callback;
    private ArrayList<Game> currentGames;
    private boolean headerEnabled = false;

    public final static int ITEM_HEADER = 1;
    public final static int ITEM_NORMAL = 2;

    public GamesAdapter(Context c) {
        context = c;
        try {
            callback = (GamePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GamePickedListener.");
        }
    }

    public void setData(List<Game> games, int sortId) {
        if (sortId == SORT_ALPHABETICALLY) {
            sortAlphabetically(games);
        } else if (sortId == SORT_HOURS_PLAYED) {
            sortHoursPlayed(games);
        }
        dataSet.clear();
        dataSetCopy.clear();
        dataSet.addAll(games);
        dataSetCopy.addAll(games);
        notifyDataSetChanged();
    }

    private void sortAlphabetically(List<Game> games) {
        Collections.sort(games, new Comparator<Game>() {
            @Override
            public int compare(Game game1, Game game2) {
                return game1.name.toLowerCase().compareTo(game2.name.toLowerCase());
            }
        });
    }

    private void sortHoursPlayed(List<Game> games) {
        Collections.sort(games, Collections.reverseOrder());
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

    public void setCurrentGames(ArrayList<Game> games) {
        currentGames = games;
        notifyDataSetChanged();
    }

    public void setHeaderEnabled(boolean b) {
        if (headerEnabled != b) {
            headerEnabled = b;
            notifyDataSetChanged();
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view;
        if (viewType == ITEM_HEADER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.games_header_item, parent, false);
            return new VHHeader(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.games_item, parent, false);
            return new VHItem(view);
        }
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
        if (holder instanceof VHHeader) {
            final VHHeader header = (VHHeader) holder;
            header.button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onGamesPicked(dataSet);
                    currentGames.clear();
                    currentGames.addAll(dataSet);
                    notifyDataSetChanged();
                }
            });
        } else if (holder instanceof VHItem){
            final VHItem item = (VHItem) holder;
            final Game game = dataSet.get(headerEnabled ? position - 1 : position);
            item.name.setText(game.name);
            final int quantity = game.hoursPlayed < 1 ? 0 : (int) Math.ceil(game.hoursPlayed);
            item.hours.setText(context.getResources()
                    .getQuantityString(R.plurals.hours_on_record, quantity, game.hoursPlayed));
            if (!PrefsManager.minimizeData()) {
                Glide.with(context)
                        .load(game.iconUrl)
                        .into(item.logo);
            } else {
                item.logo.setImageResource(R.drawable.ic_image_white_48dp);
            }

            item.itemView.setActivated(currentGames.contains(game));

            item.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!currentGames.contains(game) && currentGames.size() < 32) {
                        currentGames.add(game);
                        item.itemView.setActivated(true);
                        callback.onGamePicked(game);
                    } else {
                        currentGames.remove(game);
                        item.itemView.setActivated(false);
                        callback.onGameRemoved(game);
                    }
                }
            });

            item.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    callback.onGameLongPressed(game);
                    return true;
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        if (headerEnabled) {
            return dataSet.size() + 1;
        }
        return dataSet.size();
    }

    private static class VHHeader extends RecyclerView.ViewHolder {
        private Button button;

        private VHHeader(View itemView) {
            super(itemView);
            button = (Button) itemView;
        }
    }

    private static class VHItem extends RecyclerView.ViewHolder {
        private TextView name;
        private ImageView logo;
        private TextView hours;

        private VHItem(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            logo = itemView.findViewById(R.id.logo);
            hours = itemView.findViewById(R.id.hours);
        }
    }
}
