package com.steevsapps.idledaddy.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

public class HomeFragment extends Fragment {
    private final static String TAG = HomeFragment.class.getSimpleName();

    public final static String LOGGED_IN = "LOGGED_IN";
    public final static String FARMING = "FARMING";

    // Status message
    private View status;
    private ImageView statusImg;
    private TextView statusText;
    private ViewGroup dropInfo;
    private TextView cardCountText;
    private TextView gameCountText;

    private TextView startIdling;
    private Button stopIdling;

    private ViewGroup nowPlaying;
    private TextView game;
    private ImageView gameIcon;
    private TextView cardDropsRemaining;
    private ImageView pauseResumeButton;
    private ImageView nextButton;

    private boolean loggedIn = false;
    private boolean farming = false;

    public static HomeFragment newInstance(boolean loggedIn, boolean farming) {
        final HomeFragment fragment = new HomeFragment();
        final Bundle args = new Bundle();
        args.putBoolean(LOGGED_IN, loggedIn);
        args.putBoolean(FARMING, farming);
        fragment.setArguments(args);
        return fragment;
    }

    public void update(boolean loggedIn, boolean farming) {
        this.loggedIn = loggedIn;
        this.farming = farming;
        updateStatus();
    }

    /**
     * Show the drop info card
     */
    public void showDropInfo(int gameCount, int cardCount) {
        dropInfo.setVisibility(View.VISIBLE);
        gameCountText.setText(getResources().getQuantityString(R.plurals.games_left, gameCount, gameCount));
        cardCountText.setText(getResources().getQuantityString(R.plurals.card_drops_remaining, cardCount, cardCount));
    }

    /**
     * Hide the drop info card
     */
    public void hideDropInfo() {
        dropInfo.setVisibility(View.GONE);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loggedIn = getArguments().getBoolean(LOGGED_IN, false);
        farming = getArguments().getBoolean(FARMING, false);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.home_fragment, container, false);
        status = view.findViewById(R.id.status);
        statusImg = view.findViewById(R.id.status_img);
        statusText = view.findViewById(R.id.status_text);
        dropInfo = view.findViewById(R.id.drop_info);
        cardCountText = view.findViewById(R.id.card_count);
        gameCountText = view.findViewById(R.id.game_count);
        startIdling = view.findViewById(R.id.start_idling);
        nowPlaying = view.findViewById(R.id.now_playing_card);
        game = view.findViewById(R.id.game);
        gameIcon = view.findViewById(R.id.icon);
        cardDropsRemaining = view.findViewById(R.id.card_drops_remaining);
        pauseResumeButton = view.findViewById(R.id.pause_resume_button);
        nextButton = view.findViewById(R.id.next_button);
        updateStatus();
        return view;
    }

    private void setStatusOnline() {
        status.setClickable(false);
        statusImg.setImageResource(R.drawable.ic_check_circle_white_48dp);
        statusText.setText(R.string.logged_in);
    }

    private void setStatusOffline() {
        status.setClickable(true);
        statusImg.setImageResource(R.drawable.ic_error_white_48dp);
        statusText.setText(R.string.tap_to_login);
    }

    /**
     * show/hide the "Now Playing" card
     */
    public void showNowPlaying(List<Game> games, boolean isFarming, boolean isPaused) {
        if (games.isEmpty()) {
            // Hide the Now playing card
            nowPlaying.setVisibility(View.GONE);
            return;
        }
        // Currently just show the first game
        final Game g = games.get(0);
        if (!PrefsManager.minimizeData()) {
            Glide.with(getActivity()).load(g.iconUrl).into(gameIcon);
        } else {
            gameIcon.setImageResource(R.drawable.ic_image_white_48dp);
        }
        game.setText(g.name);
        if (g.dropsRemaining > 0) {
            // Show card drops remaining
            cardDropsRemaining.setText(getResources()
                    .getQuantityString(R.plurals.card_drops_remaining, g.dropsRemaining, g.dropsRemaining));
        } else {
            // No card drops. Show hours played instead
            final int quantity = g.hoursPlayed < 1 ? 0 : (int) Math.ceil(g.hoursPlayed);
            cardDropsRemaining.setText(getResources()
                    .getQuantityString(R.plurals.hours_on_record, quantity, g.hoursPlayed));
        }
        // Show the pause or resume button depending on if we're paused or not.
        pauseResumeButton.setImageResource(isPaused ? R.drawable.ic_action_play : R.drawable.ic_action_pause);
        // Hide the "next" when idling multiple
        nextButton.setVisibility((isFarming && games.size() == 1) ? View.VISIBLE : View.GONE);
        // Show the card
        nowPlaying.setVisibility(View.VISIBLE);
    }

    private void updateStatus() {
        if (loggedIn) {
            setStatusOnline();
        } else {
            setStatusOffline();
        }
        startIdling.setEnabled(loggedIn && !farming);
    }
}
