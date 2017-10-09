package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;

import java.util.List;

/**
 * Options dialog shown when you long press a game
 */
public class GameOptionsDialog extends DialogFragment {
    public final static String TAG = GameOptionsDialog.class.getSimpleName();

    private final static String TITLE = "TITLE";
    private final static String APPID = "APPID";
    private String title;
    private String appId;

    public static GameOptionsDialog newInstance(Game game) {
        final GameOptionsDialog fragment = new GameOptionsDialog();
        final Bundle args = new Bundle();
        args.putString(TITLE, game.name);
        args.putString(APPID, String.valueOf(game.appId));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        title = args.getString(TITLE);
        appId = args.getString(APPID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setItems(R.array.game_long_press_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int position) {
                        if (position == 0) {
                            // Blacklist
                            addToBlacklist();
                        }
                    }
                })
                .create();
    }

    private void addToBlacklist() {
        final List<String> blacklist = Prefs.getBlacklist();
        if (!blacklist.contains(appId)) {
            blacklist.add(0, appId);
            Prefs.writeBlacklist(blacklist);
            Toast.makeText(getActivity(), R.string.added_to_blacklist, Toast.LENGTH_LONG).show();
        }
    }
}
