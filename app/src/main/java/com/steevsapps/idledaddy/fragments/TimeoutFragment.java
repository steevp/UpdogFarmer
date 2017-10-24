package com.steevsapps.idledaddy.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.steevsapps.idledaddy.listeners.TimeoutListener;

/**
 * Timeout handler that survives screen rotation
 */
public class TimeoutFragment extends Fragment {
    public final static String TAG = TimeoutFragment.class.getSimpleName();
    private TimeoutListener callback;

    private final static int TIMEOUT_MILLIS = 25000;
    private final Handler timeoutHandler = new Handler();
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (callback != null) {
                callback.onTimeout();
            }
        }
    };

    public static TimeoutFragment newInstance() {
        return new TimeoutFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (TimeoutListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement TimeoutListener");
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
        // Start timeout
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS);
    }

    @Override
    public void onDestroy() {
        // Stop timeout
        timeoutHandler.removeCallbacks(timeoutRunnable);
        super.onDestroy();
    }
}
