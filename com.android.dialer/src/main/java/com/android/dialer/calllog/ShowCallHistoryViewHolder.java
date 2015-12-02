package com.android.dialer.calllog;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.dialer.R;

public final class ShowCallHistoryViewHolder extends RecyclerView.ViewHolder {

    private ShowCallHistoryViewHolder(final Context context, View view) {
        super(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(context, CallLogActivity.class);
                context.startActivity(intent);
            }
        });
    }

    public static ShowCallHistoryViewHolder create(Context context, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.show_call_history_list_item, parent, false);
        return new ShowCallHistoryViewHolder(context, view);
    }
}
