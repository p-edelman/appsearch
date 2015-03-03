package com.mrpi.appsearch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.util.Log;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Calendar;

/** The accompanying widget for the search app. It displays the icons for the
 *  three most used apps for the current time, next to an app to launch the
 *  search.
 *  Most widget events are broadcasted as intents. This class catches these
 *  intents and handles them by itself.
 *  This class also implements the SearchThreadListener interface, which
 *  provides a mechanism for search AsyncTasks to communicate back their
 *  results. An update is performed by calling a SearchMostUsedThread and letting
 *  the onSearchThreadFinished() method update the widget display. */
public class SmartIcon
        extends AppWidgetProvider
        implements SearchThreadListener {

  /** Intent actions defined within this class. Because of the widget
   *  architecture in Android, interacting with the widget is done via intents.
   *  We capture and handle them directly within this class. */
  public static String ACTION_WIDGET_ICON_CLICK = "ACTION_WIDGET_ICON_CLICK";
  public static String ACTION_WIDGET_UPDATE     = "ACTION_WIDGET_UPDATE";

  /** Constants for the preferences. */
  public final static String SMART_ICON_PREFERENCES = "PreferencesSmartIcon";
  public final static String SMART_ICON_LAYOUT      = "SMART_ICON_LAYOUT";
  public final static String SMART_ICON_CONFIG_SHOW = "SMART_ICON_CONFIG_SHOW";

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
    updateWidgetStart(context);
    super.onUpdate(context, widget_manager, widget_ids);

    // See if we should configure a widget
    configureWidget(widget_ids, context);
  }

  /** Set the updating of the widget display in motion.
   *  This method launches a SearchMostUsedThread to find the top apps. When it's
   *  done, the onSearchThreadFinished() method is called to handle the result.
   *  @param context the application context for this widget */
  private void updateWidgetStart(Context context) {
    Log.d("Widget", "Updating app widget");

    SearchMostUsedThread search_thread = new SearchMostUsedThread(context, this);
    search_thread.execute(0);
  }

  /** Set the icons in (all) the active widget(s) to the list that was found by
   *  the SearchMostUsedThread.
   *  @param apps a list of apps, sorted in order of relevance descending
   *  @param context the application context. This is needed for the
   *                 SearchMostUsedThread. */
  public void onSearchThreadFinished(ArrayList<AppData> apps, Context context) {
    // Instantiate the RemoteViews object for the app widget layout.
    SharedPreferences preferences = context.getSharedPreferences(SMART_ICON_PREFERENCES,
                                                                 Context.MODE_MULTI_PROCESS);
    int layout_index  = preferences.getInt(SMART_ICON_LAYOUT, 0);
    String layout_str = context.getResources().getStringArray(R.array.smart_icon_layouts_resource_names)[layout_index];
    int layout_id     = context.getResources().getIdentifier(layout_str, "layout",
                                                              context.getPackageName());
    Log.d("Widget", "Using layout " + layout_str);
    RemoteViews views = new RemoteViews(context.getPackageName(),
                                        layout_id);

    PackageManager package_manager = context.getPackageManager();

    // Get all the widget ids
    ComponentName component  = new ComponentName(context, SmartIcon.class);
    AppWidgetManager manager = AppWidgetManager.getInstance(context);
    int[] widget_ids = manager.getAppWidgetIds(component);

    // Get the top apps and set them to the icons
    int app_num    = 0;
    int widget_num = 0;
    while(app_num < apps.size() && widget_num < widget_ids.length) { // Safeguard for when apps from the database are uninstalled in the meantime
      AppData app = apps.get(app_num);
      Log.d("Widget", "Working on app " + app.name);
      try {
        // Get the app icon
        ApplicationInfo app_info = package_manager.getApplicationInfo(app.package_name,
                                                                      PackageManager.GET_META_DATA);
        Resources resources = package_manager.getResourcesForApplication(app_info);
        Bitmap icon         = BitmapFactory.decodeResource(resources, app_info.icon);

        // Set icon and name
        views.setImageViewBitmap(R.id.widget_icon, icon);
        views.setTextViewText(R.id.widget_text, app.name);

        // For responding to touch, we first need to create an internal intent (that
        // can be caught by onReceive()). Then we wrap this intent in a
        // PendingIntent, that we can bind to an icon.
        // Because the intents for all icons look the same (they only differ in the
        // extra data), Android will not create a new PendingIntent object for each
        // icon. Therefore, we need to set a different request code for all of them
        // AND set the FLAG_UPDATE_CURRENT, which will update the current
        // PendingIntent with the new intent.
        Intent intent = new Intent(context, SmartIcon.class);
        intent.setAction(ACTION_WIDGET_ICON_CLICK);
        intent.putExtra("name",         app.name);
        intent.putExtra("package_name", app.package_name);
        PendingIntent pending_intent = PendingIntent.getBroadcast(context, app_num, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_icon, pending_intent);
        views.setOnClickPendingIntent(R.id.widget_text, pending_intent);

        manager.updateAppWidget(widget_ids[widget_num], views);

        widget_num++;
      } catch (PackageManager.NameNotFoundException e) {
        // App is not there anymore, by silently ignoring this we skip to the
        // next app in the list
      }
      app_num++;
    }
  }

  /** Called when the first widget is installed.
   *  This method sets an AlarmManager to fire at (1 second past) the start of
   *  every 5 minute slot (that is also used for scoring app relevance) to
   *  update all active widgets. */
  @Override
  public void onEnabled(Context context) {
    Log.d("Widget", "Widget installment, enabling alarm manager");
    super.onEnabled(context);

    // Prepare an update intent to fire every five minutes to this class.
    Intent intent = new Intent(context, SmartIcon.class);
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

    // Now set the alarmmanager to repeat every five minutes.
    AlarmManager alarm_manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    alarm_manager.setRepeating(AlarmManager.RTC, time.getTimeInMillis(), 5 * 60 * 1000, pending_intent);
  }

  /** Called when all widgets are removed. This method cancels the running
   *  AlarmManager. */
  @Override
  public void onDisabled(Context context) {
    Log.d("Widget", "Last widget deleted, disabling alarm manager");
    Intent intent                = new Intent(context, SmartIcon.class);
    PendingIntent pending_intent = PendingIntent.getBroadcast(context, 0, intent, 0);
    AlarmManager alarm_manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm_manager.cancel(pending_intent);
    super.onDisabled(context);
  }

  /** Handle intents to this class.
   *  The AppWidgetProvider base class uses thus mechanism quite extensively, so
   *  we filter out only the relevant intents and call through to the base class
   *  for the rest. */
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(ACTION_WIDGET_UPDATE)) {
      Log.d("Widget", "Received a signal to update");
      updateWidgetStart(context);
    } else if (intent.getAction().equals(ACTION_WIDGET_ICON_CLICK)) {
      // One of the app icons was clicked
      final String name = intent.getStringExtra("name");
      final String package_name = intent.getStringExtra("package_name");

      // Save the launch time slot to the database
      CountAndDecay count_decay = new CountAndDecay(DBHelper.getInstance(context));
      count_decay.countAppLaunch(name, package_name);

      // Now, launch the app.
      Log.d("Widget", "Launching app " + name);
      Intent launch_intent = context.getPackageManager().getLaunchIntentForPackage(package_name);
      context.startActivity(launch_intent);
    } else {
      super.onReceive(context, intent);
    }
  }

  /** Check the database if there are unconfigured widgets and if so, make sure
   *  it is configured; the widget_id is set to congigured in the database and
   *  the configuration widget is launched (unless this has been disabled by a
   *  user setting).
   *  @param widget_ids the current list of ids for active widgets
   *  @param context the context in which this class runs. */
  private void configureWidget(int[] widget_ids,
                               Context context) {

    DBHelper db_helper = DBHelper.getInstance(context);
    SQLiteDatabase db;

    // See if there are id's in the list that, according to the database,
    // haven't been configured.
    Integer unconfigured_id = null;
    db = db_helper.getReadableDatabase();
    for (int widget_id: widget_ids) {
      Cursor cursor = db.query(DBHelper.TBL_WIDGET_IDS, new String[]{"widget_id"},
                               "widget_id=?", new String[]{Integer.toString(widget_id)},
                               null, null, null, null);
      if (cursor.getCount() == 0) {
        unconfigured_id = widget_id;
        break;
      }
    }

    // If we have an unconfigured widget, save it as being configured (even if
    // we don't show the dialog; it's configured with the default settings
    // then).
    if (unconfigured_id != null) {
      db = db_helper.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put("widget_id", unconfigured_id);
      db.insert(DBHelper.TBL_WIDGET_IDS, null, values);

      // Open the configuration dialog, if the user hasn't disabled it
      SharedPreferences preferences = context.getSharedPreferences(SMART_ICON_PREFERENCES,
                                                                   Context.MODE_MULTI_PROCESS);
      if (preferences.getBoolean(SMART_ICON_CONFIG_SHOW, true)) {
        Intent launch_intent = new Intent(context, SmartIconConfig.class);
        launch_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch_intent);
      }
    }
  }

  @Override
  public void onDeleted(Context context,
                        int[] widget_ids) {
    // Since we're saving the id of each configured widget, we have to remove
    // them from the settings when it is deleted.
    DBHelper db_helper = DBHelper.getInstance(context);
    for (int widget_id: widget_ids) {
      SQLiteDatabase db = db_helper.getWritableDatabase();
      String[] where_args = {Integer.toString(widget_id)};
      db.delete(DBHelper.TBL_WIDGET_IDS, "widget_id=?", where_args);
    }
  }
}