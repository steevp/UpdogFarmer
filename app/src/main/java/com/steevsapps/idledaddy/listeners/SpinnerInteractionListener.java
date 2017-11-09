package com.steevsapps.idledaddy.listeners;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.fragments.GamesFragment;

/**
 * Many events can trigger the onItemSelected call, and it is difficult to keep track of all of them.
 * This solution allows you to only respond to user-initiated changes using an OnTouchListener.
 */
public class SpinnerInteractionListener implements AdapterView.OnItemSelectedListener, View.OnTouchListener {
    private boolean userSelect = false;
    private FragmentManager fm;

    public SpinnerInteractionListener(FragmentManager fm) {
        this.fm = fm;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        userSelect = true;
        return false;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
        if (userSelect) {
            handleSelection(position);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void handleSelection(int position) {
        final Fragment fragment = fm.findFragmentById(R.id.content_frame);
        if (fragment instanceof GamesFragment) {
            final GamesFragment gamesFragment = (GamesFragment) fragment;
            switch (position) {
                case GamesFragment.TAB_GAMES:
                    // Library
                    gamesFragment.switchToGames();
                    break;
                case GamesFragment.TAB_LAST:
                    // Last idling session
                    gamesFragment.switchToLastSession();
                    break;
                case GamesFragment.TAB_BLACKLIST:
                    // Blacklisted games
                    gamesFragment.switchToBlacklist();
                    break;
            }
        }
    }
}
