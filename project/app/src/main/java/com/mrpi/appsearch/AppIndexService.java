package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Service to index the available apps on the system.
 * <p>
 * Indexing all launchable apps with their specifics to launch them, can be
 * relatively slow in some unfavorable circumstances - in the order of seconds
 * even. To prevent the slowdown, this is relegated to this service that will be
 * run each time the app is brought to the foreground.
 * <p>
 * This problem could also be solved with a thread, but using a service has
 * the advantage that it keeps running when the app is closed. In the use case
 * that a user opens the app, accidentally closes it, and reopens it again,
 * a recent list of apps is already present (this service will be run again,
 * but the app can already start).
 * <p>
 * The apps are sorted according to popularity for this moment.
 */
public class AppIndexService
        extends IntentService
        implements SearchThreadListener {

  public AppIndexService() {
    super("AppIndexService");
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    Log.d("AppSearch", "Updating app index");

    // Get the apps with known scores and wait for this process to finish.
    SearchMostUsedThread search_thread = new SearchMostUsedThread(this, this);
    search_thread.execute();
  }

  /** When the list of popular apps is created, we can continue with the
   *  indexing process. */
  public void onSearchThreadFinished(ArrayList<AppData> popular_apps,
                                     Context context) {
    // Get the installed apps
    ArrayList<AppData> installed_apps = queryApps();

    // Remove all entries in popular_apps from installed_apps and then append
    // the result to popular_apps. This way, we get a list of all the apps
    // sorted by popularity score.
    installed_apps.removeAll(popular_apps);
    popular_apps.addAll(installed_apps);

    // Write back the result to the database
    writeToDB(popular_apps);
  }

  /** Query the system for installed apps.
   *  @return a list of AppData objects of all the installed apps on the system,
   *          except for this app itself. */
  private ArrayList<AppData> queryApps() {
    // We need to filter out ourselves
    String own_name = getPackageName();

    // Get a list of installed apps
    final PackageManager pm = getPackageManager();
    final Intent main_intent = new Intent(Intent.ACTION_MAIN, null);
    main_intent.addCategory(Intent.CATEGORY_LAUNCHER);
    final List<ResolveInfo> packages = pm.queryIntentActivities(main_intent, 0);

    // Collect the relevant info in an AppData object
    ArrayList<AppData> app_list = new ArrayList<AppData>();
    for (ResolveInfo resolve_info : packages) {
      ActivityInfo activity_info = resolve_info.activityInfo;
      AppData app_data = new AppData(resolve_info.loadLabel(pm).toString(),
                                     activity_info.applicationInfo.packageName);
      if (!app_data.package_name.equals(own_name)) { // Exclude self from list
        app_list.add(app_data);
      }
    };

    return app_list;
  }

  /** Write the list of apps to the dirty table in the database, and then
   *  switch this around for the live app table.
   *  This needs to be done in a single step, and not during indexing, because
   *  that process can be quite slow and so would prevent search access to the
   *  database while indexing.
   *  @param apps list of apps to write to the database. */
  private void writeToDB(ArrayList<AppData> apps) {
    DBHelper db_helper = DBHelper.getInstance(this);
    SQLiteDatabase db  = db_helper.getWritableDatabase();

    db.beginTransactionNonExclusive();
    for (AppData app_data: apps) {
      ContentValues values = new ContentValues();
      values.put("public_name",  app_data.name);
      values.put("package_name", app_data.package_name);
      db.replace(DBHelper.TBL_APPS_DIRTY, null, values);
    }
    db.setTransactionSuccessful();
    db.endTransaction();

    Log.d("AppSearch", "Indexing completed in the dirty table");
    db_helper.switchDirty();
  }
}