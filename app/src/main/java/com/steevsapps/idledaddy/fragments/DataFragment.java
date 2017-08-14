package com.steevsapps.idledaddy.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless fragment that holds the game list during configuration changes
 */
public class DataFragment extends Fragment {
    private List<Game> data;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public List<Game> getData() {
        if (data == null) {
            return new ArrayList<>();
        }
        return data;
    }

    public void setData(List<Game> data) {
        this.data = data;
    }
}
