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

import com.steevsapps.idledaddy.BaseActivity;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.SteamWebHandler;

public class SummerEventDialog extends DialogFragment implements View.OnClickListener {
    public final static String TAG = SummerEventDialog.class.getSimpleName();

    private SummerEventViewModel viewModel;

    private TextView statusTv;
    private TextView countdownTv;
    private Button playSaliensBtn;
    private Button playSaliensFullBtn;

    public static SummerEventDialog newInstance() {
        return new SummerEventDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.summer_event_dialog, null);
        statusTv = view.findViewById(R.id.status);
        countdownTv = view.findViewById(R.id.countdown);
        playSaliensBtn = view.findViewById(R.id.btn_play_saliens);
        playSaliensBtn.setOnClickListener(this);
        playSaliensFullBtn = view.findViewById(R.id.btn_play_saliens_full);
        playSaliensFullBtn.setOnClickListener(this);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.summer_event)
                .setView(view)
                .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
    }

    private void setupViewModel(){
        viewModel = ViewModelProviders.of(this).get(SummerEventViewModel.class);
        viewModel.init(SteamWebHandler.getInstance());
        viewModel.getStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                statusTv.setVisibility(View.VISIBLE);
                statusTv.setText(s);
                playSaliensBtn.setEnabled(viewModel.isFinished());
                playSaliensFullBtn.setEnabled(viewModel.isFinished());
            }
        });
        viewModel.getCountDown().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                countdownTv.setVisibility(View.VISIBLE);
                countdownTv.setText(s);
            }
        });
        viewModel.getRefreshEvent().observe(this, new Observer<Void>() {
            @Override
            public void onChanged(@Nullable Void aVoid) {
                // Refresh Steam Session
                final SteamService service = ((BaseActivity) getActivity()).getService();
                if (service != null) {
                    service.refreshSession();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play_saliens:
                viewModel.playSaliens();
                break;
            case R.id.btn_play_saliens_full:
                viewModel.playSaliensFull();
                break;
        }
    }
}
