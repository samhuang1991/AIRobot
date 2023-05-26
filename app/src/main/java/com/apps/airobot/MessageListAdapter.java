package com.apps.airobot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.ViewHolder> {


    public MessageListAdapter() {

    }

    @Override
    public MessageListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == 1) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
        }

        //必须添加监听才能有选中效果
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理子项点击事件
                LogUtil.i("item onClick!");
            }
        });

        return new MessageListAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageListAdapter.ViewHolder holder, int position) {
        ChatItem message = mApi.chatItems.get(position);
        holder.messageTextView.setText(message.getText());
    }

    @Override
    public int getItemCount() {
        return mApi.chatItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem bean = mApi.chatItems.get(position);
        return bean.getType();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView messageTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
        }
    }
}
