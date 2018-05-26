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
import com.steevsapps.idledaddy.steam.SteamWebHandler;

public class SpringCleaningDialog extends DialogFragment implements View.OnClickListener {
    public final static String TAG = SpringCleaningDialog.class.getSimpleName();

    private Button dailyTasksBtn;
    private Button projectsBtn;
    private TextView statusTv;
    private SpringCleaningViewModel viewModel;

    public static SpringCleaningDialog newInstance() {
        return new SpringCleaningDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.spring_cleaning_dialog, null);
        dailyTasksBtn = view.findViewById(R.id.btn_daily_tasks);
        projectsBtn = view.findViewById(R.id.btn_projects);
        statusTv = view.findViewById(R.id.status);
        dailyTasksBtn.setOnClickListener(this);
        projectsBtn.setOnClickListener(this);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.spring_cleaning_title)
                .setView(view)
                .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_daily_tasks:
                viewModel.completeDailyTasks();
                break;
            case R.id.btn_projects:
                viewModel.completeProjectTasks();
                break;
        }
    }

    private void setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(SpringCleaningViewModel.class);
        viewModel.init(SteamWebHandler.getInstance(), ((BaseActivity) getActivity()).getService());
        viewModel.getStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                statusTv.setVisibility(View.VISIBLE);
                statusTv.setText(s);
                dailyTasksBtn.setEnabled(viewModel.isFinished());
                projectsBtn.setEnabled(viewModel.isFinished());
            }
        });
    }
}
