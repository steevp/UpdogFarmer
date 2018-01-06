package com.steevsapps.idledaddy.adapters;

import android.support.v7.util.ListUpdateCallback;

/**
 * This ListUpdateCallback considers that when the list header is enabled,
 * item positions will be off by one.
 */
class GamesListUpdateCallback implements ListUpdateCallback {
    private GamesAdapter adapter;
    private int offset;

    GamesListUpdateCallback(GamesAdapter adapter, boolean headerEnabled) {
        this.adapter = adapter;
        this.offset = headerEnabled ? 1 : 0;
    }

    @Override
    public void onInserted(int position, int count) {
        adapter.notifyItemRangeInserted(position + offset, count);
    }

    @Override
    public void onRemoved(int position, int count) {
        adapter.notifyItemRangeRemoved(position + offset, count);
    }

    @Override
    public void onMoved(int fromPosition, int toPosition) {
        adapter.notifyItemMoved(fromPosition + offset, toPosition + offset);
    }

    @Override
    public void onChanged(int position, int count, Object payload) {
        adapter.notifyItemRangeChanged(position + offset, count, payload);
    }
}
