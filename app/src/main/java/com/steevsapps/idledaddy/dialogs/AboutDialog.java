package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.webkit.WebView;

import com.steevsapps.idledaddy.R;

public class AboutDialog extends DialogFragment {
    public static AboutDialog newInstance() {
        return new AboutDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final WebView webView = (WebView) LayoutInflater.from(getActivity()).inflate(R.layout.about_dialog, null);
        webView.loadUrl("file:///android_asset/about.html");
        return new AlertDialog.Builder(getActivity(), R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setTitle("About")
                .setView(webView)
                .setPositiveButton(R.string.ok, null)
                .create();
    }
}
