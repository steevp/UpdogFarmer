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
import android.widget.EditText;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.listeners.DialogListener;

public class RedeemDialog extends DialogFragment {
    private DialogListener callback;

    public static RedeemDialog newInstance() {
        return new RedeemDialog();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (DialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement DialogListener.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.redeem);
        builder.setMessage(R.string.redeem_msg);
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.redeem_dialog, null);
        final EditText input = view.findViewById(R.id.input);
        builder.setView(view);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (callback != null) {
                    callback.onYesPicked(input.getText().toString());
                }
            }
        });
        return builder.create();
    }
}
