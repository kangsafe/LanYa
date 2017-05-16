package com.ks.lanya;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Admin on 2017/5/15 0015 11:34.
 * Author: kang
 * Email: kangsafe@163.com
 */

public class BlueAdapter extends BaseAdapter {
    Context mContext;

    public BlueAdapter(Context context) {
        this.mContext = context;
    }

    public List<DeviceInfo> datas = new ArrayList<>();

    @Override
    public int getCount() {
        return datas.size();
    }

    @Override
    public Object getItem(int position) {
        return datas.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BlueViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item, parent, false);
            holder = new BlueViewHolder();
            holder._name = (TextView) convertView.findViewById(R.id.name);
            holder._addr = (TextView) convertView.findViewById(R.id.addr);
            convertView.setTag(holder);
        } else {
            holder = (BlueViewHolder) convertView.getTag();
        }

        DeviceInfo m = datas.get(position);
        holder._name.setText(m.getName());
        holder._addr.setText(m.getAddr());

        return convertView;
    }
}
