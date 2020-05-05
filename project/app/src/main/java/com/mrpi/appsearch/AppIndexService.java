package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.List;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Service to index the available apps on the system.
 *
 * Indexing all launchable apps with their specifics to launch them, can be
 * relatively slow in some unfavorable circumstances - in the order of seconds
 * even. To prevent the slowdown, this is relegated to this service that will be
 * run each time the app is brought to the foreground.
 *
 * This problem could also be solved with a thread, but using a service has
 * the advantage that it keeps running when the app is closed. In the use case
 * that a user opens the app, accidentally closes it, and reopens it again,
 * a recent list of apps is already present (this service will be run again,
 * but the app can already start).
 *
 * The apps are sorted according to popularity for this moment.
 */
public class AppIndexService
        extends IntentService {

    public AppIndexService() {
        super("AppIndexService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("AppSearch", "Updating app index");

        // Get the apps with known scores.
        ArrayList<AppSearchResult> popular_apps = null;
        MostUsedAppsSearcher searcher = new MostUsedAppsSearcher(this, -1);
        popular_apps = searcher.search();

        // Get the installed apps
        ArrayList<AppSearchResult> installed_apps = queryApps();

        if (popular_apps != null) {
            // Remove all entries in popular_apps from installed_apps and then append
            // the result to popular_apps. This way, we get a list of all the apps
            // sorted by popularity score.
            installed_apps.removeAll(popular_apps);
            popular_apps.addAll(installed_apps);

            // Write back the result to the database
            writeToDB(popular_apps);
        } else {
            writeToDB(installed_apps);
        }

    }

    /**
     * Query the system for installed apps.
     *
     * @return a list of AppSearchResult objects of all the installed apps on the system,
     *         except for this app itself.
     */
    private ArrayList<AppSearchResult> queryApps() {
        // We need to filter out ourselves
        String own_name = getPackageName();

        // Get a list of installed apps
        final PackageManager pm = getPackageManager();
        final Intent main_intent = new Intent(Intent.ACTION_MAIN, null);
        main_intent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> packages = pm.queryIntentActivities(main_intent, 0);

        // Collect the relevant info in an AppSearchResult object
        ArrayList<AppSearchResult> app_list = new ArrayList<AppSearchResult>();
        for (ResolveInfo resolve_info : packages) {
            ActivityInfo activity_info = resolve_info.activityInfo;
            AppSearchResult app_data = new AppSearchResult(activity_info.loadLabel(pm).toString(),
                    activity_info.applicationInfo.packageName);
            if (!app_data.package_name.equals(own_name)) { // Exclude self from list
                app_list.add(app_data);
            }
        }

        return app_list;
    }

    /**
     * Write the list of apps to the dirty table in the database, and then
     * switch this around for the live app table.
     * This needs to be done in a single step, and not during indexing, because
     * that process can be quite slow and so would prevent search access to the
     * database while indexing.
     *
     * @param apps list of apps to write to the database.
     */
    private void writeToDB(ArrayList<AppSearchResult> apps) {
        DBHelper db_helper = DBHelper.getInstance(this);
        SQLiteDatabase db = db_helper.getWritableDatabase();

        db.beginTransactionNonExclusive();
        for (AppSearchResult app_data : apps) {
            ContentValues values = new ContentValues();
            values.put("public_name", app_data.name);
            values.put("package_name", app_data.package_name);
            db.replace(DBHelper.TBL_APPS_DIRTY, null, values);
        }
        db.setTransactionSuccessful();
        db.endTransaction();

        Log.d("AppSearch", "Indexing completed in the dirty table");
        db_helper.switchDirty();
    }
}