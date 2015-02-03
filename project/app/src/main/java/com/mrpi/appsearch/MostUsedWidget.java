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
 *  intents and handles them by itself.
 *  This class also implements the SearchThreadListener interface, which
 *  provides a mechanism for search AsyncTasks to communicate back their
 *  results. An update is performed by calling a SearchMostUsedThread and letting
 *  the onSearchThreadFinished() method update the widget display. */
public class MostUsedWidget
        extends AppWidgetProvider
        implements SearchThreadListener {

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
    updateWidgetStart(context);
    super.onUpdate(context, widget_manager, widget_ids);
  }

  /** Set the updating of the widget display in motion.
   *  This method launches a SearchMostUsedThread to find the top apps. When it's
   *  done, the onSearchThreadFinished() method is called to handle the result.
   *  @param context the applciation context for this widget */
  private void updateWidgetStart(Context context) {
    Log.d("Widget", "Updating app widget");

    SearchMostUsedThread search_thread = new SearchMostUsedThread(context, this);
    search_thread.execute(3);
  }

  /** Set the icons in (all) the active widget(s) to the list that was found by
   *  the SearchMostUsedThread.
   *  @param apps a list of apps, sorted in order of relevance descending
   *  @param context the application context. This is needed for the
   *                 SearchMostUsedThread. */
  public void onSearchThreadFinished(ArrayList<AppData> apps, Context context) {
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

  /** Handle intents to this class.
   *  The AppWidgetProvider base class uses thus mechanism quite extensively, so
   *  we filter out only the relevant intents and call through to the base class
   *  for the rest. */
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(ACTION_WIDGET_UPDATE)) {
      Log.d("Widget", "Received an alarm schedule to update");
      updateWidgetStart(context);
    } else if (intent.getAction().equals(ACTION_WIDGET_SEARCH)) {
      // Open the search app
      Intent launch_intent = new Intent(context, MainActivity.class);
      launch_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(launch_intent);
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
}
