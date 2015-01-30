package com.mrpi.appsearch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
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

/** The accompanying widget for the search app. It displays the icons for the
 *  three most used apps for the current time, next to an app to launch the
 *  search.
 *  Most widget events are broadcasted as intents. This class catches these
 *  intents and handles them by itself. */
public class MostUsedWidget extends AppWidgetProvider {

  /** Intent actions defined within this class. Because of the widget
   *  architecture in Android, interacting with the widget is done via intents.
   *  We capture and handle them directly within this class. */
  public static String ACTION_WIDGET_ICON_CLICK = "ACTION_WIDGET_ICON_CLICK";
  public static String ACTION_WIDGET_SEARCH     = "ACTION_WIDGET_SEARCH";
  public static String ACTION_WIDGET_UPDATE     = "ACTION_WIDGET_UPDATE";

  /** Called by the system each time the widget is updated. This actually
   *  happens only once, the very first time the widget is instantiated. From
   *  this time on, an AlarmManager runs to update the widget every five
   *  minutes.
   *  @param context the application context
   *  @param widget_manager the active AppWidgetManager
   *  @param widget_ids a list of all the widget id's. */
  @Override
  public void onUpdate(Context          context,
                       AppWidgetManager widget_manager,
                       int[]            widget_ids) {
    updateWidget(context);
    super.onUpdate(context, widget_manager, widget_ids);
  }

  /** Update the widget display.
   *  This method searches for the three top apps and sets the icons of (all)
   *  the active widget(s) them.
   *  @param context the applciation context for this widget */
  private void updateWidget(Context context) {
    Log.d("Widget", "Updating app widget");

    // Instantiate the RemoteViews object for the app widget layout.
    RemoteViews views = new RemoteViews(context.getPackageName(),
                                        R.layout.most_used_widget);

    PackageManager package_manager = context.getPackageManager();

    // For responding to touch, we first need to create an internal intent (that
    // can be caught by onReceive()). Then we wrap this intent in a
    // PendingIntent, that we can bind to an icon.
    // Because the intents for all icons look the same (they only differ in the
    // extra data), Android will not create a new PendingIntent object for each
    // icon. Therefore, we need to set a different request code for all of them
    // AND set the FLAG_UPDATE_CURRENT, which will update the current
    // PendingIntent with the new intent.
    Intent intent = new Intent(context, MostUsedWidget.class);
    intent.setAction(ACTION_WIDGET_SEARCH);
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
        intent.setAction(ACTION_WIDGET_ICON_CLICK);
        intent.putExtra("name",         app.name);
        intent.putExtra("package_name", app.package_name);
        pending_intent = PendingIntent.getBroadcast(context, app_num, intent,
                                                    PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(icon_id, pending_intent);
        views.setOnClickPendingIntent(text_id, pending_intent);

        drawn_apps++;
      } catch (PackageManager.NameNotFoundException e) {
        // App is not there anymore, by silently ignoring this we skip to the
        // next app in the list
      }
      app_num++;
    }

    // Update all the widgets with the new view
    ComponentName widget_id = new ComponentName(context, MostUsedWidget.class);
    AppWidgetManager manager = AppWidgetManager.getInstance(context);
    manager.updateAppWidget(widget_id, views);
  }

  /** Called when the first widget is installed.
   *  This method sets an AlarmManager to fire at (1 second past) the start of
   *  every 5 minute slot (that is also used for scoring app relevance) to
   *  update all active widgets. */
  @Override
  public void onEnabled(Context context) {
    super.onEnabled(context);

    // Prepare an update intent to fire every five minutes to this class.
    Intent intent = new Intent(context, MostUsedWidget.class);
    intent.setAction(ACTION_WIDGET_UPDATE);
    PendingIntent pending_intent = PendingIntent.getBroadcast(context, 0, intent, 0);

    // Construct the first time when the first update should fire, thus the next
    // start of a time slot.
    Calendar time = Calendar.getInstance();
    int minutes = time.get(Calendar.MINUTE);
    int minute_slot = minutes / 5;
    int minutes_diff = ((minute_slot + 1) * 5) - minutes;
    time.add(Calendar.MINUTE, minutes_diff);
    time.set(Calendar.SECOND, 1);
    time.set(Calendar.MILLISECOND, 0);

    // Now set the alarmmanager to reapeat every five minutes.
    AlarmManager alarm_manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    alarm_manager.setRepeating(AlarmManager.RTC, time.getTimeInMillis(), 5 * 60 * 1000, pending_intent);
  }

  /** Called when all widgets are removed. This method cancels the running
   *  AlarmManager. */
  @Override
  public void onDisabled(Context context) {
    Intent intent                = new Intent(context, MostUsedWidget.class);
    PendingIntent pending_intent = PendingIntent.getBroadcast(context, 0, intent, 0);
    AlarmManager alarm_manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm_manager.cancel(pending_intent);
    super.onDisabled(context);
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

  /** Handle intents to this class.
   *  The AppWidgetProvider base class uses thus mechanism quite extensively, so
   *  we filter out only the relevant intents and call through to the base class
   *  for the rest. */
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(ACTION_WIDGET_SEARCH)) {
      // Open the search app
      Intent launch_intent = new Intent(context, MainActivity.class);
      launch_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(launch_intent);
    } else if (intent.getAction().equals(ACTION_WIDGET_ICON_CLICK)) {
      // One of the app icons was clicked
      final String name = intent.getStringExtra("name");
      final String package_name = intent.getStringExtra("package_name");

      // Save the launch time slot to the database
      CountAndDecay count_decay = new CountAndDecay(AppCacheOpenHelper.getInstance(context));
      count_decay.countAppLaunch(name, package_name);

      // Now, launch the app.
      Log.d("Widget", "Launching app " + name);
      Intent launch_intent = context.getPackageManager().getLaunchIntentForPackage(package_name);
      context.startActivity(launch_intent);
    } else if (intent.getAction().equals(ACTION_WIDGET_UPDATE)) {
      Log.d("Widget", "Received an alarm schedule to update");
      updateWidget(context);
    } else {
      super.onReceive(context, intent);
    }
  }

}


