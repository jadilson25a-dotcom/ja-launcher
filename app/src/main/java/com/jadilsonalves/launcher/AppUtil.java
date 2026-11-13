package com.jadilsonalves.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Funções de apoio para listar, abrir e configurar apps. */
final class AppUtil {

    static final String PREFS = "ja_launcher";

    private AppUtil() {
    }

    static final class AppEntry {
        final String pkg;
        final String label;

        AppEntry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Intent para abrir o app, priorizando a versão para TV. */
    static Intent launchIntent(Context c, String pkg) {
        if (pkg == null) return null;
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Exception ignored) {
        }
        if (i == null) {
            try {
                i = pm.getLaunchIntentForPackage(pkg);
            } catch (Exception ignored) {
            }
        }
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    static boolean isLaunchable(Context c, String pkg) {
        return launchIntent(c, pkg) != null;
    }

    static boolean launch(Context c, String pkg) {
        Intent i = launchIntent(c, pkg);
        if (i == null) return false;
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre uma tela específica de outro app (ex.: "com.pacote/.Tela"). */
    static boolean openComponent(Context c, String flat) {
        try {
            ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn == null) return false;
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre por uma ação de intent, sem cair nas configurações gerais se falhar. */
    static boolean openAction(Context c, String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Tenta abrir uma tela de configurações; se não existir, tenta a próxima. */
    static boolean openSettings(Context c, String... actions) {
        for (String action : actions) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void openAppInfo(Context c, String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    static String label(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static Drawable icon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    static Drawable banner(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /** Todos os apps que podem ser abertos (TV e celular), em ordem alfabética. */
    static List<AppEntry> listApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> found = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> list;
            try {
                list = pm.queryIntentActivities(q, 0);
            } catch (Exception e) {
                continue;
            }
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || found.containsKey(pkg)) continue;
                CharSequence lbl = ri.loadLabel(pm);
                found.put(pkg, lbl != null ? lbl.toString() : pkg);
            }
        }
        List<AppEntry> apps = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            apps.add(new AppEntry(e.getKey(), e.getValue()));
        }
        final Collator collator = Collator.getInstance(new Locale("pt", "BR"));
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });
        return apps;
    }
}
