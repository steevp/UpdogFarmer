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
import android.widget.TextView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.SteamWebHandler;

public class AutoDiscoverDialog extends DialogFragment {
    public final static String TAG = AutoDiscoverDialog.class.getSimpleName();
    private AutoDiscoverViewModel viewModel;
    private TextView textView;

    public static AutoDiscoverDialog newInstance() {
        return new AutoDiscoverDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        textView = (TextView) LayoutInflater.from(getActivity()).inflate(R.layout.auto_discover_dialog, null);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.auto_discovery_title)
                .setMessage(R.string.auto_discovery)
                .setPositiveButton(android.R.string.ok, null)
                .setView(textView)
                .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = ViewModelProviders.of(this).get(AutoDiscoverViewModel.class);
        viewModel.init(SteamWebHandler.getInstance());
        viewModel.getQueueItem().observe(this, new Observer<AutoDiscoverViewModel.QueueItem>() {
            @Override
            public void onChanged(@Nullable AutoDiscoverViewModel.QueueItem queueItem) {
                handleQueueItem(queueItem);
            }
        });
    }

    private void handleQueueItem(@Nullable AutoDiscoverViewModel.QueueItem queueItem) {
        if (queueItem != null) {
            textView.setText(getString(R.string.discovering, queueItem.appId, queueItem.number, queueItem.count));
        } else {
            textView.setText(viewModel.getResult() ? R.string.discovery_finished : R.string.discovery_error);
        }
    }
}