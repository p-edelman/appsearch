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
 * <p>
 * Indexing all launchable apps with their specifics to launch it, can be 
 * relatively slow - in the order of seconds. To prevent the UI from blocking,
 * this is relegated to this service that will be run each time the app is 
 * brought to the foreground (to have an up-to-date list).
 * <p>
 * This problem could also be solved with a thread, but using a service has
 * the advantage that it keeps running when the app is closed. In the use case
 * that a user opens the app, accidentally closes it, and reopens it again,
 * a recent list of apps is already present (this service will be run again,
 * but the app can already start).
 */
public class AppIndexService extends IntentService {

  public AppIndexService() {
    super("AppIndexService");
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    Log.d("AppSearch", "Updating app index");
    // Get a list of launchable apps.
    final PackageManager pm = getPackageManager();
    final Intent main_intent = new Intent(Intent.ACTION_MAIN, null);
    main_intent.addCategory(Intent.CATEGORY_LAUNCHER);
    final List<ResolveInfo> packages = pm.queryIntentActivities(main_intent, 0);
        
    // Index the necessary info for each app
    String own_name = getString(R.string.app_name);
    ArrayList<AppData> app_list = new ArrayList<AppData>();
    for (ResolveInfo resolve_info : packages) {
      AppData app_data = new AppData();
      ActivityInfo activity_info = resolve_info.activityInfo;
      app_data.name          = resolve_info.loadLabel(pm).toString();
      app_data.package_name  = activity_info.applicationInfo.packageName.toString();
      if (!app_data.name.equals(own_name)) { // Exclude self from list
        app_list.add(app_data);
      }
    };

    // Put all we found in the database. We do this in a separate loop and not
    // during indexing, because that process is quite slow and so would prevent
    // search access to the database while indexing.
    AppCacheOpenHelper cache = AppCacheOpenHelper.getInstance(getBaseContext());
    SQLiteDatabase db = cache.getWritableDatabase();
    db.beginTransaction();
    for (AppData app_data: app_list) {
      ContentValues values = new ContentValues();
      values.put("public_name",  app_data.name);
      values.put("package_name", app_data.package_name);
      db.replace(AppCacheOpenHelper.TBL_APPS_DIRTY, null, values);
    }
    db.setTransactionSuccessful();
    db.endTransaction();

    Log.d("AppSearch", "Indexing completed in the dirty table");
    cache.switchDirty();
  }

}
