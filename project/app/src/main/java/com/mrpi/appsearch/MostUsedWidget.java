package com.mrpi.appsearch;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** The accompanying widget for the search app. It displays the icons for the three most used apps
 *  for the current time, next to an app to launch te search. */
public class MostUsedWidget extends AppWidgetProvider {

  /** Intent actions defined within this class. We capture and handle icon clicks within this class,
   *  and because of the widget architecture this is done by catching Intents. */
  public static String WIDGET_ICON_CLICK = "WIDGET_ICON_CLICK";
  public static String WIDGET_SEARCH     = "WIDGET_SEARCH";

  /** Called by the system each time the widget is updated.
   *  This method searches for the three top apps and sets the icons of (all) the active widget(s)
   *  them.
   *  @param context the application context
   *  @param widget_manager the active AppWidgetManager
   *  @param widget_ids a list of all the widget id's. */
  @Override
  public void onUpdate(Context          context,
                       AppWidgetManager widget_manager,
                       int[]            widget_ids) {

    // Instantiate the RemoteViews object for the app widget layout.
    RemoteViews views = new RemoteViews(context.getPackageName(),
                                        R.layout.most_used_widget);

    PackageManager package_manager = context.getPackageManager();

    // For responding to touch, we first need to create an internal intent (that is caught by
    // onReceive). Then we wrap this intent in a PendingIntent, that we can bind to an icon.
    // Because the intents for all icons look the same (they only differ in the extra data),
    // Android will reuse the same PendingIntent object each time. Therefore, we need to set a
    // different request code for all of them AND set the FLAG_UPDATE_CURRENT, which will update the
    // current PendingIntent with the new intent.
    Intent intent = new Intent(context, MostUsedWidget.class);
    intent.setAction(WIDGET_SEARCH);
    PendingIntent pending_intent = PendingIntent.getBroadcast(context, -1, intent,
                                                              PendingIntent.FLAG_UPDATE_CURRENT);
    views.setOnClickPendingIntent(R.id.widget_icon_search, pending_intent);
    views.setOnClickPendingIntent(R.id.widget_text_search, pending_intent);

    // Get the top apps and set them to the icons
    ArrayList<AppData> apps = getMostUsedApps(context);
    int app_num    = 0;
    int drawn_apps = 0;
    while(app_num < apps.size() && drawn_apps < 3) { // Safeguard for when apps from the database are uninstalled in the meantime
      AppData app = apps.get(app_num);
      try {
        ApplicationInfo app_info = package_manager.getApplicationInfo(app.package_name,
                                                                      PackageManager.GET_META_DATA);

        // Set icon and name
        Resources resources = package_manager.getResourcesForApplication(app_info);
        Bitmap icon         = BitmapFactory.decodeResource(resources, app_info.icon);
        int icon_id = context.getResources().getIdentifier("widget_icon_" + drawn_apps, "id", context.getPackageName());
        int text_id = context.getResources().getIdentifier("widget_text_" + drawn_apps, "id", context.getPackageName());
        views.setImageViewBitmap(icon_id, icon);
        views.setTextViewText(text_id, app.name);

        // Set intent for when the user clicks
        intent = new Intent(context, MostUsedWidget.class);
        intent.setAction(WIDGET_ICON_CLICK);
        intent.putExtra("name",         app.name);
        intent.putExtra("package_name", app.package_name);
        pending_intent = PendingIntent.getBroadcast(context, app_num, intent,
                                                    PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(icon_id, pending_intent);
        views.setOnClickPendingIntent(text_id, pending_intent);

        drawn_apps++;
      } catch (PackageManager.NameNotFoundException e) {
        // App is not there anymore, by silently ignoring this we skip to the next app in the list
      }
      app_num++;
    }

    // Update all the widgets with the new view
    for (int i = 0; i < widget_ids.length; i++) {
      widget_manager.updateAppWidget(widget_ids[i], views);
    }
    super.onUpdate(context, widget_manager, widget_ids);
  }

  /** Retrieve a list with the most used apps from the database. */
  static private ArrayList<AppData> getMostUsedApps(Context context) {
    ArrayList<AppData> apps = new ArrayList<AppData>();

    SQLiteDatabase db = null;
    try {
      db = AppCacheOpenHelper.getInstance(context).getReadableDatabase();
    } catch (SQLiteDatabaseLockedException e) {
      // TODO: Handle this properly
      Log.d("AppSearch", "Can't get a lock on the database!");
    }

    if (db != null) {
      long time_slot = CountAndDecay.getTimeSlot();
      int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
      String time_slot_str = Long.toString(time_slot);
      String day_str = Integer.toString(day);

      // Create an entry for each app with the score calculated by clicks and
      // day.
      Map<String, AppData> app_map = new HashMap<String, AppData>();

      // Get the top eight apps for this time slot and day
      Cursor cursor = db.query(AppCacheOpenHelper.TBL_USAGE_WEEK,
              new String[]{"public_name", "package_name", "count"},
              "time_slot=? AND day=?",
              new String[]{time_slot_str, day_str},
              null, null,
              "count DESC", "8");
      convertCursorToAppData(cursor, app_map);
      Log.d("MostUsed", "Got results for time and day");
      cursor.close();

      // Get the top eight apps for this time slot only
      cursor = db.query(AppCacheOpenHelper.TBL_USAGE_DAY,
              new String[]{"public_name", "package_name", "count"},
              "time_slot=?",
              new String[]{time_slot_str},
              null, null,
              "count DESC", "8");
      convertCursorToAppData(cursor, app_map);
      Log.d("MostUsed", "Got results for time");
      cursor.close();

      // Get the top eight apps overall
      cursor = db.query(AppCacheOpenHelper.TBL_USAGE_ALL,
              new String[]{"public_name", "package_name", "count"},
              null, null, null, null,
              "count DESC", "8");
      convertCursorToAppData(cursor, app_map);
      Log.d("MostUsed", "Got results overall");
      cursor.close();

      // Now convert it to an ArrayList and sort the results by the ratings, in
      // descending order.
      apps = new ArrayList<AppData>(app_map.values());
      Collections.sort(apps, new Comparator<Object>() {
        public int compare(Object obj1, Object obj2) {
          return ((AppData) obj2).match_rating - ((AppData) obj1).match_rating;
        }
      });
    }

    return apps;
  }

  static void convertCursorToAppData(Cursor cursor, Map<String, AppData> app_map) {
    boolean result = cursor.moveToFirst();
    while (result) {
      String app_name = cursor.getString(0);
      AppData app_data = app_map.get(app_name);
      if (app_data == null) {
        app_data = new AppData();
        app_data.name         = app_name;
        app_data.package_name = cursor.getString(1);
        app_data.match_rating = cursor.getInt(2);
        app_map.put(app_name, app_data);
      } else {
        if (cursor.getInt(2) > app_data.match_rating) {
          app_data.match_rating = cursor.getInt(2);
        }
      }
      Log.d("SearchWidget", "Rating for " + app_name + " is " + app_data.match_rating);
      result = cursor.moveToNext();
    }
  }

  /** We override this method to catch internal intents generated by a click on one of the icons. */
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(WIDGET_SEARCH)) {
      // Open the search app
      Intent launch_intent = new Intent(context, MainActivity.class);
      launch_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(launch_intent);
    } else if (intent.getAction().equals(WIDGET_ICON_CLICK)) {
      // One of the app icons was clicked
      final String name         = intent.getStringExtra("name");
      final String package_name = intent.getStringExtra("package_name");

      // Save the launch time slot to the database
      CountAndDecay count_decay = new CountAndDecay(AppCacheOpenHelper.getInstance(context));
      count_decay.countAppLaunch(name, package_name);

      // Now, launch the app.
      Log.d("Widget", "Launching app " + name);
      Intent launch_intent = context.getPackageManager().getLaunchIntentForPackage(package_name);
      context.startActivity(launch_intent);
    } else {
      super.onReceive(context, intent);
    }
  }

}


