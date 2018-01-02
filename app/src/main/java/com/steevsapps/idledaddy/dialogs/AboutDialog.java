package com.steevsapps.idledaddy.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.webkit.WebView;

import com.steevsapps.idledaddy.R;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AboutDialog extends DialogFragment {
    public final static String TAG = AboutDialog.class.getSimpleName();

    public static AboutDialog newInstance() {
        return new AboutDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final WebView webView = (WebView) LayoutInflater.from(getActivity()).inflate(R.layout.about_dialog, null);
        final String lang = Locale.getDefault().getLanguage();
        String uri = "file:///android_asset/about.html";
        try {
            // Load language-specific version of the about page if available.
            final List<String> assets = Arrays.asList(getResources().getAssets().list(""));
            if (assets.contains(String.format("about-%s.html", lang))) {
                uri = String.format("file:///android_asset/about-%s.html", lang);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        webView.loadUrl(uri);
        webView.setBackgroundColor(Color.TRANSPARENT);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.about)
                .setView(webView)
                .setPositiveButton(R.string.ok, null)
                .create();
    }
}
