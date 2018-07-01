package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.SteamWebHandler;

public class AutoDiscoverDialog extends DialogFragment implements View.OnClickListener {
    public final static String TAG = AutoDiscoverDialog.class.getSimpleName();
    private AutoDiscoverViewModel viewModel;
    private TextView statusTv;
    private Button autoDiscoverBtn;

    public static AutoDiscoverDialog newInstance() {
        return new AutoDiscoverDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.auto_discover_dialog, null);
        statusTv = view.findViewById(R.id.status);
        autoDiscoverBtn = view.findViewById(R.id.btn_auto_discover);
        autoDiscoverBtn.setOnClickListener(this);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.auto_discovery_title)
                .setView(view)
                .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
    }

    private void setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(AutoDiscoverViewModel.class);
        viewModel.init(SteamWebHandler.getInstance());
        viewModel.getStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                statusTv.setVisibility(View.VISIBLE);
                statusTv.setText(s);
                autoDiscoverBtn.setEnabled(viewModel.isFinished());
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_auto_discover:
                viewModel.autodiscover();
                break;
        }
    }
}