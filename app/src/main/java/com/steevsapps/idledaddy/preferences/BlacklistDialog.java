package com.steevsapps.idledaddy.preferences;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.adapters.BlacklistAdapter;

public class BlacklistDialog extends PreferenceDialogFragmentCompat implements View.OnClickListener, EditText.OnEditorActionListener {
    private final static String VALUE = "VALUE"; // Key to hold current value

    private EditText input;
    private ImageView addButton;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private BlacklistAdapter adapter;
    private BlacklistPreference preference;
    private String currentValue;

    public static BlacklistDialog newInstance(Preference preference) {
        final BlacklistDialog fragment = new BlacklistDialog();
        final Bundle args = new Bundle(1);
        args.putString("key", preference.getKey());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preference = (BlacklistPreference) getPreference();
        if (savedInstanceState == null) {
            currentValue = preference.getValue();
        } else {
            currentValue = savedInstanceState.getString(VALUE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(VALUE, adapter.getValue());
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        input = view.findViewById(R.id.input);
        input.setOnEditorActionListener(this);
        addButton = view.findViewById(R.id.add);
        addButton.setOnClickListener(this);
        recyclerView = view.findViewById(R.id.recycler_view);
        layoutManager = new LinearLayoutManager(recyclerView.getContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new BlacklistAdapter(currentValue);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            preference.persistStringValue(adapter.getValue());
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.add:
                addItem();
                break;
        }
    }

    private void addItem() {
        final String text = input.getText().toString().trim();
        if (text.matches("\\d+")) {
            adapter.addItem(text);
            input.setText("");
            recyclerView.scrollToPosition(0);
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            // Submit
            addItem();
            return true;
        }
        return false;
    }
}
