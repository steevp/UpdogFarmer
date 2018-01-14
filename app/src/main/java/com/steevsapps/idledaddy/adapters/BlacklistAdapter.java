package com.steevsapps.idledaddy.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlacklistAdapter extends RecyclerView.Adapter<BlacklistAdapter.ViewHolder> {
    private List<String> dataSet = new ArrayList<>();

    public BlacklistAdapter(String data) {
        data = data.trim();
        if (!data.isEmpty()) {
            dataSet.addAll(Arrays.asList(data.split(",")));
        }
    }

    public String getValue() {
        return Utils.arrayToString(dataSet);
    }

    public void addItem(String item) {
        if (!dataSet.contains(item)) {
            dataSet.add(0, item);
            notifyItemInserted(0);
        }
    }

    private void removeItem(int position) {
        dataSet.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.blacklist_dialog_item, parent, false);
        final ViewHolder vh = new ViewHolder(view);
        vh.removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeItem(vh.getAdapterPosition());
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final String appId = dataSet.get(position);
        holder.appId.setText(appId);
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView appId;
        private ImageView removeButton;
        private ViewHolder(View itemView) {
            super(itemView);
            appId = itemView.findViewById(R.id.appid);
            removeButton = itemView.findViewById(R.id.remove_button);
        }
    }
}
