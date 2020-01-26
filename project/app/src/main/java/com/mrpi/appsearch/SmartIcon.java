package com.mrpi.appsearch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
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
  public final static String ICON_SIZE_P            = "ICON_SIZE_P";
  public final static String ICON_SIZE_L            = "ICON_SIZE_L";
  public final static String ICON_PADDING_P         = "ICON_PADDING_P";
  public final static String ICON_PADDING_L         = "ICON_PADDING_L";
  public final static String TEXT_SIZE_P            = "TEXT_SIZE_P";
  public final static String TEXT_SIZE_L            = "TEXT_SIZE_L";
  public final static String TEXT_PADDING_P         = "TEXT_PADDING_P";
  public final static String TEXT_PADDING_L         = "TEXT_PADDING_L";
  public final static String TEXT_BOLD              = "TEXT_BOLD";
  public final static String TEXT_ITALIC            = "TEXT_ITALIC";
  public final static String HAS_BACKGROUND         = "HAS_BACKGROUND";
  public final static String IS_CONFIGURED          = "IS_CONFIGURED";

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
    context.startService(new Intent(context, SmartIconRotationService.class));
    super.onUpdate(context, widget_manager, widget_ids);
  }

  /** Set the updating of the widget display in motion.
   *  This method launches a SearchMostUsedThread to find the top apps. When it's
   *  done, the onSearchThreadFinished() method is called to handle the result.
   *  @param context the application context for this widget */
  private void updateWidgetStart(Context context) {
    Log.d("Widget", "Updating app widget");

    SearchMostUsedThread search_thread = new SearchMostUsedThread(context, this);
    ComponentName component            = new ComponentName(context, SmartIcon.class);
    AppWidgetManager manager           = AppWidgetManager.getInstance(context);
    search_thread.execute(manager.getAppWidgetIds(component).length);
  }

  /** Set the icons in (all) the active widget(s) to the list that was found by
   *  the SearchMostUsedThread.
   *  @param apps a list of apps, sorted in order of relevance descending
   *  @param context the application context. This is needed for the
   *                 SearchMostUsedThread. */
  public void onSearchThreadFinished(ArrayList<AppData> apps, Context context) {
    PackageManager package_manager = context.getPackageManager();
    Resources resources = context.getResources();
    SharedPreferences preferences = context.getSharedPreferences(SMART_ICON_PREFERENCES,
            Context.MODE_MULTI_PROCESS);

    // Instantiate the RemoteViews object for the app widget layout.
    RemoteViews views = new RemoteViews(context.getPackageName(),
            R.layout.smart_icon);

    // Set background
    if (preferences.getBoolean(HAS_BACKGROUND, true)) {
      views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.smart_icon_background);
    } else {
      views.setInt(R.id.widget_container, "setBackgroundResource", 0);
    }

    // Get the orientation dependent parameters
    int   icon_padding;
    int   text_padding;
    float text_size    = resources.getDimension(R.dimen.smart_icon_text_size_default);
    int   icon_size    = resources.getDimensionPixelSize(android.R.dimen.app_icon_size);
    if (resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
      icon_padding = preferences.getInt(SmartIcon.ICON_PADDING_P, 0);
      text_padding = preferences.getInt(SmartIcon.TEXT_PADDING_P, 0);
      text_size    = preferences.getFloat(SmartIcon.TEXT_SIZE_P, text_size);
      icon_size    = preferences.getInt(SmartIcon.ICON_SIZE_P, icon_size);
    } else {
      icon_padding = preferences.getInt(SmartIcon.ICON_PADDING_L, 0);
      text_padding = preferences.getInt(SmartIcon.TEXT_PADDING_L, 0);
      text_size    = preferences.getFloat(SmartIcon.TEXT_SIZE_L, text_size);
      icon_size    = preferences.getInt(SmartIcon.ICON_SIZE_L, icon_size);
    }

    // Set app independent parameters
    views.setViewPadding(R.id.widget_icon, 0, icon_padding, 0, 0);
    views.setViewPadding(R.id.widget_text, 0, text_padding, 0, 0);
    views.setFloat(R.id.widget_text, "setTextSize", text_size);

    // To loop over all the installed widgets, we need to get all the widget ids
    ComponentName component  = new ComponentName(context, SmartIcon.class);
    AppWidgetManager manager = AppWidgetManager.getInstance(context);
    int[] widget_ids = manager.getAppWidgetIds(component);

    // Fill the widgets with the top apps
    int app_num     = 0;
    for (int widget_num = 0; widget_num < widget_ids.length; widget_num++) {
      // Try to find a matching app
      ApplicationInfo app_info      = null;
      Resources       app_resources = null;
      AppData         app           = null;
      while (app_num < apps.size() && app_info == null) {
        app = apps.get(app_num);
        try {
          app_info = package_manager.getApplicationInfo(app.package_name,
            PackageManager.GET_META_DATA);
          app_resources = package_manager.getResourcesForApplication(app_info);
        } catch (PackageManager.NameNotFoundException e) {
          // App is not there anymore
          app_num++;
        }
      }

      // Load app icon and name or defaults
      Drawable icon_raw;
      String label;
      if (app_resources != null) {
        icon_raw = package_manager.getApplicationIcon(app_info);
        label    = app.name;
      } else {
        // We're out of apps, use default icons and text
        icon_raw = context.getResources().getDrawable(android.R.drawable.ic_delete);
        label    = context.getResources().getString(R.string.no_app_name);
      }

      // Render application icon to widget format and set it to the widget
      Bitmap icon_scaled = Bitmap.createBitmap(icon_size, icon_size, Bitmap.Config.ARGB_8888);
      Canvas icon_canvas = new Canvas(icon_scaled);
      icon_raw.setBounds(0, 0, icon_size, icon_size);
      icon_raw.draw(icon_canvas);
      views.setImageViewBitmap(R.id.widget_icon, icon_scaled);

      // Set widget label
      Spannable spannable = new SpannableString(label);
      if (preferences.getBoolean(TEXT_BOLD, false)) {
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), 0);
      }
      if (preferences.getBoolean(TEXT_ITALIC, false)) {
        spannable.setSpan(new StyleSpan(Typeface.ITALIC), 0, label.length(), 0);
      }
      views.setTextViewText(R.id.widget_text, spannable);

      if (app_resources != null) {
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
        intent.putExtra("name", app.name);
        intent.putExtra("package_name", app.package_name);
        PendingIntent pending_intent = PendingIntent.getBroadcast(context, app_num, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_icon, pending_intent);
        views.setOnClickPendingIntent(R.id.widget_text, pending_intent);
      } else {
        // Make sure any intents attached from the previous round are cleared
        views.setOnClickPendingIntent(R.id.widget_icon, null);
        views.setOnClickPendingIntent(R.id.widget_text, null);
      }

      manager.updateAppWidget(widget_ids[widget_num], views);
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

   // Configure the widget if needed
   SharedPreferences preferences = context.getSharedPreferences(SMART_ICON_PREFERENCES,
                                                                Context.MODE_MULTI_PROCESS);
   if (!preferences.getBoolean(IS_CONFIGURED, false)) {
     Intent launch_intent = new Intent(context, SmartIconConfig.class);
     launch_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
     context.startActivity(launch_intent);
   }
  }

  /** Called when all widgets are removed. This method cancels the running
   *  AlarmManager. */
  @Override
  public void onDisabled(Context context) {
    // Disable alarammanager
    Log.d("Widget", "Last widget deleted, disabling alarm manager");
    Intent intent                = new Intent(context, SmartIcon.class);
    PendingIntent pending_intent = PendingIntent.getBroadcast(context, 0, intent, 0);
    AlarmManager alarm_manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarm_manager.cancel(pending_intent);

   // We leave everything again "unconfigured"; it's likely that when the user ever returns, a new
   // configuration is required.
   SharedPreferences preferences = context.getSharedPreferences(SMART_ICON_PREFERENCES,
                                                                Context.MODE_MULTI_PROCESS);
   SharedPreferences.Editor editor = preferences.edit();
   editor.putBoolean(IS_CONFIGURED, false);
   editor.apply();

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
      count_decay.countAppLaunch(package_name);

      // Now, launch the app.
      Log.d("Widget", "Launching app " + name);
      Intent launch_intent = context.getPackageManager().getLaunchIntentForPackage(package_name);
      context.startActivity(launch_intent);
    } else {
      super.onReceive(context, intent);
    }
  }
}