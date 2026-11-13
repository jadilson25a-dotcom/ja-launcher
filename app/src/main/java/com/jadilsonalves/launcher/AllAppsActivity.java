package com.jadilsonalves.launcher;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Grade com todos os apps instalados. */
public class AllAppsActivity extends Activity {

    private final List<AppUtil.AppEntry> apps = new ArrayList<>();
    private AppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        GridView grid = findViewById(R.id.grid);
        adapter = new AppsAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppUtil.launch(AllAppsActivity.this, apps.get(position).pkg);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AppUtil.openAppInfo(AllAppsActivity.this, apps.get(position).pkg);
                return true;
            }
        });
        grid.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarrega sempre, para refletir apps instalados ou removidos.
        apps.clear();
        apps.addAll(AppUtil.listApps(this));
        adapter.notifyDataSetChanged();
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(AllAppsActivity.this)
                        .inflate(R.layout.item_app, parent, false);
            }
            AppUtil.AppEntry e = apps.get(position);
            ImageView icon = v.findViewById(R.id.app_icon);
            Drawable d = AppUtil.banner(AllAppsActivity.this, e.pkg);
            if (d == null) d = AppUtil.icon(AllAppsActivity.this, e.pkg);
            icon.setImageDrawable(d);
            ((TextView) v.findViewById(R.id.app_label)).setText(e.label);
            return v;
        }
    }
}
