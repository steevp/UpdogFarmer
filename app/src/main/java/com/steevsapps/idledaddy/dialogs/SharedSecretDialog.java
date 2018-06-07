package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.preferences.PrefsManager;

public class SharedSecretDialog extends DialogFragment implements View.OnClickListener {
    public final static String TAG = SharedSecretDialog.class.getSimpleName();
    private final static String STEAM_ID = "STEAM_ID";

    private TextView statusTv;
    private Button sharedSecretBtn;
    private Button enterManuallyBtn;
    private SharedSecretViewModel viewModel;

    public static SharedSecretDialog newInstance(long steamId) {
        final Bundle args = new Bundle();
        args.putLong(STEAM_ID, steamId);
        final SharedSecretDialog fragment = new SharedSecretDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.shared_secret_dialog, null);

        statusTv = view.findViewById(R.id.status);

        sharedSecretBtn = view.findViewById(R.id.btn_shared_secret);
        sharedSecretBtn.setOnClickListener(this);

        enterManuallyBtn = view.findViewById(R.id.btn_enter_manually);
        enterManuallyBtn.setOnClickListener(this);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.import_shared_secret)
                .setView(view)
                .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
    }

    private void setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(SharedSecretViewModel.class);
        viewModel.init(getArguments().getLong(STEAM_ID));
        viewModel.getStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                statusTv.setText(s);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_shared_secret:
                viewModel.getSharedSecret();
                break;
            case R.id.btn_enter_manually:
                showManualDialog();
                break;
        }
    }

    private void showManualDialog() {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.enter_shared_secret_dialog, null);
        final EditText editText = view.findViewById(R.id.shared_secret_input);
        editText.setText(PrefsManager.getSharedSecret());
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_shared_secret)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String text = editText.getText().toString().trim();
                        if (!text.isEmpty()) {
                            PrefsManager.writeSharedSecret(text);
                            viewModel.setValue(getString(R.string.your_shared_secret, text));
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
