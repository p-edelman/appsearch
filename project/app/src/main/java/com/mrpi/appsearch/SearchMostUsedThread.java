package com.mrpi.appsearch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/** Class for finding the most used apps in a background thread. */
public class SearchMostUsedThread extends SearchThread {

  public SearchMostUsedThread(Context context, SearchThreadListener listener) {
    super(context, listener);
  }

  /** Query the database to find the most used apps.
    * @param params should be empty (so no params). */
  @Override
  protected ArrayList<AppData> doInBackground(Object... params) {
    // Our return object
    ArrayList<AppData> apps = null;

    // Open the database
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
      Map<String, AppData> app_map = new TreeMap<String, AppData>();

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

      // Get the top eight apps overall
      cursor = db.query(AppCacheOpenHelper.TBL_USAGE_ALL,
              new String[]{"public_name", "package_name", "count"},
              null, null, null, null,
              "count DESC", "8");
      convertCursorToAppData(cursor, app_map);
      Log.d("MostUsed", "Got results overall");
      cursor.close();

      // Sort the apps by ranking and generate an ArrayList with the sorted
      // apps.
      if (!isCancelled()) {
        apps = new ArrayList<AppData>(app_map.values());
        Collections.sort(apps, new Comparator<Object>() {
          public int compare(Object obj1, Object obj2) {
            return ((AppData)obj2).match_rating - ((AppData)obj1).match_rating;
          }
        });
      }
    }

    return apps;
  }

  /** Convert a database cursor with the results from an entry in the mapping
   *  of package names and AppData objects. If an app is found that is already
   *  in the map, and its rating is lower, the object in the map will be updated
   *  with the higher value.
   *  @param cursor the cursor to read from, with fields of (public_name,
   *                package_name and match_rating).
   *  @param app_map the mapping between package names and AppData objects. */
  void convertCursorToAppData(Cursor cursor, Map<String, AppData> app_map) {
    boolean result = cursor.moveToFirst();
    while (result && !isCancelled()) {
      String package_name = cursor.getString(1);
      AppData app_data = app_map.get(package_name);
      if (app_data == null) {
        app_data = new AppData();
        app_data.name         = cursor.getString(0);
        app_data.package_name = package_name;
        app_data.match_rating = cursor.getInt(2);
        app_map.put(package_name, app_data);
      } else {
        if (cursor.getInt(2) > app_data.match_rating) {
          app_data.match_rating = cursor.getInt(2);
        }
      }
      Log.d("SearchMostUsedThread", "Rating for " + app_data.name + " is " + app_data.match_rating);
      result = cursor.moveToNext();
    }
  }
}
