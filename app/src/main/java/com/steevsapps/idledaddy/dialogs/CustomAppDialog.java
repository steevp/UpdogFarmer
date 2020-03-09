package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.listeners.GamePickedListener;
import com.steevsapps.idledaddy.steam.model.Game;

public class CustomAppDialog extends DialogFragment {
    public final static String TAG = CustomAppDialog.class.getSimpleName();

    private final static int TYPE_APPID = 0;
    private final static int TYPE_CUSTOM = 1;

    private EditText customApp;

    private GamePickedListener callback;

    public static CustomAppDialog newInstance() {
        return new CustomAppDialog();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (GamePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + "must implement GamePickedListener.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.custom_app_dialog, null);
        customApp = view.findViewById(R.id.custom_app);

        final Spinner spinner = view.findViewById(R.id.spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        final ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.custom_app_type_options, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.idle_custom_app)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (spinner.getSelectedItemPosition()) {
                            case TYPE_APPID:
                                idleHiddenApp();
                                break;
                            case TYPE_CUSTOM:
                                idleNonSteamApp();
                                break;
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void idleHiddenApp() {
        final String text = customApp.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            final int appId = Integer.parseInt(text);
            final Game game = new Game(appId, getString(R.string.playing_unknown_app, appId), 0, 0);
            if (callback != null) {
                callback.onGamePicked(game);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void idleNonSteamApp() {
        final String text = customApp.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        final Game game = new Game(0, text, 0, 0);
        if (callback != null) {
            callback.onGamePicked(game);
        }
    }
}
