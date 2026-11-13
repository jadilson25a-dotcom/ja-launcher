package com.jadilsonalves.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    /** Um card da tela inicial. */
    private static final class Slot {
        final String title;
        final String sub;
        final int color;
        final String[] pkgs;

        Slot(String title, String sub, String color, String... pkgs) {
            this.title = title;
            this.sub = sub;
            this.color = Color.parseColor(color);
            this.pkgs = pkgs;
        }
    }

    /** Os 8 cards padrão. O primeiro pacote instalado da lista é usado. */
    private static final Slot[] SLOTS = {
            new Slot("YouTube", "Vídeos", "#E5484D",
                    "com.google.android.youtube.tv", "com.google.android.youtube",
                    "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"),
            new Slot("Netflix", "Filmes e séries", "#2B2D3A",
                    "com.netflix.ninja", "com.netflix.mediaclient"),
            new Slot("Disney+", "Filmes e séries", "#2563EB",
                    "com.disney.disneyplus"),
            new Slot("Prime Video", "Filmes e séries", "#38A3E0",
                    "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
            new Slot("Globoplay", "TV e novelas", "#E0457B",
                    "com.globo.globotv"),
            new Slot("Spotify", "Música", "#22A852",
                    "com.spotify.tv.android", "com.spotify.music"),
            new Slot("ESPN", "Esportes", "#EA7A26",
                    "com.espn.score_center"),
            new Slot("TV ao vivo", "Canais abertos", "#5B5FE0",
                    "com.google.android.tv", "com.android.tv"),
    };

    private static final String SC_KEYSTONE = "keystone";
    private static final String SC_IMAGE = "imagem";
    private static final String SC_HDMI = "hdmi";
    private static final String SC_WIFI = "wifi";
    private static final String SC_BT = "bluetooth";
    private static final String SC_SETTINGS = "ajustes";
    private static final String SC_APPS = "apps";
    private static final String SC_CAST = "espelhar";

    // Telas do sistema do projetor HY300 (Allwinner H713 / LuminaOS).
    private static final String HY_KEYSTONE_ACTION = "action.htcsettings.prjsettings";
    private static final String[] HY_KEYSTONE = {
            "com.htc.htcsettings/.ProjectionActivity",
            "com.htc.luminaos/.activity.ProjectActivity",
    };
    private static final String HY_KEYSTONE_ALT_PKG = "com.softwinner.tcorrection";
    private static final String HY_HDMI = "com.softwinner.awlivetv/.MainActivity";
    private static final String HY_CAST = "com.softwinner.miracastReceiver/.Miracast";

    private static final String[][] SHORTCUTS = {
            {SC_KEYSTONE, "Keystone"},
            {SC_IMAGE, "Imagem"},
            {SC_HDMI, "HDMI"},
            {SC_CAST, "Espelhar"},
            {SC_WIFI, "Wi-Fi"},
            {SC_BT, "Bluetooth"},
            {SC_SETTINGS, "Ajustes"},
            {SC_APPS, "Apps"},
    };

    private LinearLayout row1, row2, bar;
    private TextView dateText, focusTitle, focusSub, memText, netText;
    private final List<View> cards = new ArrayList<>();
    private int lastCardFocus = 0;
    private boolean focusInCards = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        row1 = findViewById(R.id.row1);
        row2 = findViewById(R.id.row2);
        bar = findViewById(R.id.shortcut_bar);
        dateText = findViewById(R.id.date_text);
        focusTitle = findViewById(R.id.focus_title);
        focusSub = findViewById(R.id.focus_sub);
        memText = findViewById(R.id.mem_text);
        netText = findViewById(R.id.net_text);
        buildShortcuts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildCards();
        updateStatus();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Apertou Home estando na tela inicial: volta para o primeiro card.
        if (!cards.isEmpty()) {
            lastCardFocus = 0;
            focusInCards = true;
            cards.get(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Tela inicial: o botão voltar não fecha o launcher.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null) {
                f.performLongClick();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- Cards ----------

    private String resolveSlot(int index) {
        String custom = AppUtil.prefs(this).getString("slot_" + index, null);
        if (custom != null && AppUtil.isLaunchable(this, custom)) return custom;
        for (String p : SLOTS[index].pkgs) {
            if (AppUtil.isLaunchable(this, p)) return p;
        }
        return null;
    }

    private void buildCards() {
        row1.removeAllViews();
        row2.removeAllViews();
        cards.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        SharedPreferences prefs = AppUtil.prefs(this);

        for (int i = 0; i < SLOTS.length; i++) {
            final int index = i;
            final Slot slot = SLOTS[i];
            final String pkg = resolveSlot(i);
            final boolean custom = pkg != null && pkg.equals(prefs.getString("slot_" + i, null));
            LinearLayout row = i < 4 ? row1 : row2;
            View card = inf.inflate(R.layout.item_card, row, false);

            final String title = custom ? AppUtil.label(this, pkg) : slot.title;
            final String sub = pkg == null ? "Não instalado · OK para escolher"
                    : (custom ? "App escolhido" : slot.sub);

            ImageView icon = card.findViewById(R.id.card_icon);
            Drawable d = pkg != null ? AppUtil.icon(this, pkg) : null;
            if (d != null) icon.setImageDrawable(d);
            else icon.setImageResource(R.drawable.ic_plus);
            ((TextView) card.findViewById(R.id.card_title)).setText(title);
            ((TextView) card.findViewById(R.id.card_sub)).setText(sub);

            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(slot.color);
            card.setBackground(bg);
            if (pkg == null) card.setAlpha(0.75f);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pkg == null || !AppUtil.launch(MainActivity.this, pkg)) {
                        pickApp("Escolher app para \"" + slot.title + "\"", "slot_" + index);
                    }
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    pickApp("Escolher app para \"" + slot.title + "\"", "slot_" + index);
                    return true;
                }
            });
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    highlight(v, hasFocus, 1.08f);
                    if (hasFocus) {
                        bg.setStroke(dp(3), Color.WHITE);
                        lastCardFocus = index;
                        focusInCards = true;
                        focusSub.setText(slot.sub);
                        focusTitle.setText(title);
                    } else {
                        bg.setStroke(0, Color.TRANSPARENT);
                    }
                }
            });
            row.addView(card);
            cards.add(card);
        }

        if (focusInCards && lastCardFocus < cards.size()) {
            cards.get(lastCardFocus).requestFocus();
        }
    }

    // ---------- Atalhos ----------

    private void buildShortcuts() {
        bar.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (String[] sc : SHORTCUTS) {
            final String key = sc[0];
            final String label = sc[1];
            View item = inf.inflate(R.layout.item_shortcut, bar, false);
            ((ImageView) item.findViewById(R.id.sc_icon)).setImageResource(iconFor(key));
            ((TextView) item.findViewById(R.id.sc_label)).setText(label);

            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(Color.TRANSPARENT);
            item.setBackground(bg);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runShortcut(key, label);
                }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (SC_APPS.equals(key)) return false;
                    pickApp("Escolher app para \"" + label + "\"", "sc_" + key);
                    return true;
                }
            });
            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    highlight(v, hasFocus, 1.12f);
                    bg.setColor(hasFocus ? Color.argb(70, 255, 255, 255) : Color.TRANSPARENT);
                    if (hasFocus) {
                        focusInCards = false;
                        focusSub.setText("Atalho");
                        focusTitle.setText(label);
                    }
                }
            });
            bar.addView(item);
        }
    }

    private int iconFor(String key) {
        switch (key) {
            case SC_KEYSTONE: return R.drawable.ic_keystone;
            case SC_IMAGE: return R.drawable.ic_image;
            case SC_HDMI: return R.drawable.ic_hdmi;
            case SC_CAST: return R.drawable.ic_cast;
            case SC_WIFI: return R.drawable.ic_wifi;
            case SC_BT: return R.drawable.ic_bluetooth;
            case SC_SETTINGS: return R.drawable.ic_settings;
            default: return R.drawable.ic_apps;
        }
    }

    private void runShortcut(String key, String label) {
        if (SC_APPS.equals(key)) {
            startActivity(new Intent(this, AllAppsActivity.class));
            return;
        }
        // Se o usuário escolheu um app para este atalho, ele tem prioridade.
        String custom = AppUtil.prefs(this).getString("sc_" + key, null);
        if (custom != null && AppUtil.launch(this, custom)) return;

        switch (key) {
            case SC_KEYSTONE:
                if (AppUtil.openAction(this, HY_KEYSTONE_ACTION)) break;
                for (String comp : HY_KEYSTONE) {
                    if (AppUtil.openComponent(this, comp)) return;
                }
                if (AppUtil.launch(this, HY_KEYSTONE_ALT_PKG)) break;
                hint(label);
                AppUtil.openSettings(this, Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case SC_IMAGE:
                AppUtil.openSettings(this, Settings.ACTION_DISPLAY_SETTINGS);
                break;
            case SC_HDMI:
                if (AppUtil.openComponent(this, HY_HDMI)) break;
                if (!AppUtil.launch(this, "com.google.android.tv")
                        && !AppUtil.launch(this, "com.android.tv")) {
                    hint(label);
                    AppUtil.openSettings(this, Settings.ACTION_SETTINGS);
                }
                break;
            case SC_CAST:
                if (!AppUtil.openComponent(this, HY_CAST)) {
                    hint(label);
                    AppUtil.openSettings(this, "android.settings.CAST_SETTINGS");
                }
                break;
            case SC_WIFI:
                AppUtil.openSettings(this, Settings.ACTION_WIFI_SETTINGS);
                break;
            case SC_BT:
                AppUtil.openSettings(this, Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            default:
                AppUtil.openSettings(this, Settings.ACTION_SETTINGS);
                break;
        }
    }

    private void hint(String label) {
        Toast.makeText(this,
                "Dica: segure OK em \"" + label + "\" para escolher o app do seu projetor",
                Toast.LENGTH_LONG).show();
    }

    // ---------- Escolher app ----------

    private void pickApp(String title, final String prefKey) {
        final List<AppUtil.AppEntry> apps = AppUtil.listApps(this);
        String[] items = new String[apps.size() + 1];
        items[0] = "↺  Restaurar padrão";
        for (int i = 0; i < apps.size(); i++) items[i + 1] = apps.get(i).label;

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor ed = AppUtil.prefs(MainActivity.this).edit();
                        if (which == 0) ed.remove(prefKey);
                        else ed.putString(prefKey, apps.get(which - 1).pkg);
                        ed.apply();
                        buildCards();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------- Status ----------

    private void updateStatus() {
        dateText.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"))
                .format(new Date()));

        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            memText.setText("Memória livre " + (mi.availMem / (1024 * 1024)) + " MB");
        } catch (Exception e) {
            memText.setText("");
        }

        String net = "Sem conexão";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                if (ni.getType() == ConnectivityManager.TYPE_WIFI) net = "Wi-Fi conectado";
                else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) net = "Cabo de rede conectado";
                else net = "Conectado";
            }
        } catch (Exception ignored) {
        }
        netText.setText(net);
    }

    // ---------- Utilidades ----------

    private void highlight(View v, boolean focused, float scale) {
        float s = focused ? scale : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(140).start();
        v.setTranslationZ(focused ? dp(8) : 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
