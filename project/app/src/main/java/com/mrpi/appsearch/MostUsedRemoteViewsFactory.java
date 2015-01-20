package com.mrpi.appsearch;

import android.appwidget.AppWidgetManager;
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
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class MostUsedRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

  Context m_context;

  ArrayList<AppData> m_apps;

  int m_widget_id;

  public MostUsedRemoteViewsFactory(Context context, Intent intent) {
    m_context   = context;
    m_widget_id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
  }

  @Override
  public void onCreate() {
    m_apps = new ArrayList<AppData>();

    SQLiteDatabase db = null;
    try {
      db = AppCacheOpenHelper.getInstance(m_context).getReadableDatabase();
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
      m_apps = new ArrayList<AppData>(app_map.values());
      Collections.sort(m_apps, new Comparator<Object>() {
        public int compare(Object obj1, Object obj2) {
          return ((AppData) obj2).match_rating - ((AppData) obj1).match_rating;
        }
      });
    }
  }

  void convertCursorToAppData(Cursor cursor, Map<String, AppData> app_map) {
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

  @Override
  public void onDataSetChanged() {

  }

  @Override
  public void onDestroy() {

  }

  @Override
  public int getCount() {
    return m_apps.size();
  }

  @Override
  public RemoteViews getViewAt(int i) {
    Log.d("SearchWidget", "Returning item at " + i + ", which is " + m_apps.get(i).name);
    // Construct a remote views item based on the app widget item XML file,
    // and set the text based on the position.
    RemoteViews views = new RemoteViews(m_context.getPackageName(), R.layout.most_used_widget_item);

    // Set icon
    PackageManager package_manager = m_context.getPackageManager();
    try {
      ApplicationInfo app_info = package_manager.getApplicationInfo(m_apps.get(i).package_name,
                                                                    PackageManager.GET_META_DATA);
      Resources resources = package_manager.getResourcesForApplication(app_info);
      Bitmap icon = BitmapFactory.decodeResource(resources, app_info.icon);
      //views.setImageViewResource(R.id.most_used_widget_icon, app_info.icon);
      //views.setImageViewResource(R.id.most_used_widget_icon, R.drawable.ic_launcher);
      views.setTextViewText(R.id.most_used_widget_text, m_apps.get(i).name);
      views.setImageViewBitmap(R.id.most_used_widget_icon, icon);

      Log.d("SearchWidget", "Set icon to " + app_info.icon);
    } catch (PackageManager.NameNotFoundException e) {
      // TODO, eh
    }

    return views;
  }

  @Override
  public RemoteViews getLoadingView() {
    return null;
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }

  @Override
  public long getItemId(int i) {
    return i;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }
}
