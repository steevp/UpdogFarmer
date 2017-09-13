package com.steevsapps.idledaddy.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.steevsapps.idledaddy.R;

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

    private Button startIdling;
    private Button stopIdling;

    private AdView adView;

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

    public void showDropInfo(int gameCount, int cardCount) {
        dropInfo.setVisibility(View.VISIBLE);
        gameCountText.setText(getResources().getQuantityString(R.plurals.games_left, gameCount, gameCount));
        cardCountText.setText(getResources().getQuantityString(R.plurals.card_drops_remaining, cardCount, cardCount));
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
        adView = view.findViewById(R.id.adView);
        updateStatus();
        loadAds();
        return view;
    }

    private void loadAds() {
        MobileAds.initialize(getActivity(), "ca-app-pub-6413501894389361~6190763130");
        final AdRequest adRequest = new AdRequest.Builder()
                // Seems ok to leave in production???
                .addTestDevice("0BCBCBBDA9FCA8FE47AEA0C5D1BCBE99")
                .addTestDevice("E8F66CC8599C1F21FDBC86370F926F88")
                .build();
        adView.loadAd(adRequest);
    }

    private void setStatusOnline() {
        status.setClickable(false);
        statusImg.setImageResource(R.drawable.ic_check_circle_white_48dp);
        statusImg.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.green));
        statusText.setText(R.string.logged_in);
    }

    private void setStatusOffline() {
        status.setClickable(true);
        statusImg.setImageResource(R.drawable.ic_error_white_48dp);
        statusImg.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.orange));
        statusText.setText(R.string.not_logged_in);
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
