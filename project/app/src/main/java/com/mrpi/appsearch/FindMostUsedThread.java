package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;

public class FindMostUsedThread extends AsyncTask<String, Void, ArrayList<AppData>> {

  // We need to remember the parent activity so that we can find the GUI element
  // for the results list.
  private Activity m_parent_activity;
  
  public FindMostUsedThread (Activity parent_activity) {
    this.m_parent_activity = parent_activity;
  }
  
  @Override
  protected ArrayList<AppData> doInBackground(String... params) {
    // The list that will hold our values
    ArrayList<AppData> app_list = new ArrayList<AppData>();

    SQLiteDatabase db;
    try {
      db = AppCacheOpenHelper.getInstance(m_parent_activity).getReadableDatabase();
    } catch (SQLiteDatabaseLockedException e) {
      // TODO: Handle this properly
      Log.d("AppSearch", "Can't get a lock on the database!");
      return app_list;
    }

    long time_slot = AppCacheOpenHelper.getTimeSlot();
    int  day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    String time_slot_str = Long.toString(time_slot);
    String day_str       = Integer.toString(day);

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

    // If the list is smaller than eight,
    // Now convert it to an ArrayList and sort the results by the ratings
    if (!isCancelled()) {
      app_list = new ArrayList<AppData>(app_map.values());
      Collections.sort(app_list, new Comparator<Object>() {
        public int compare(Object obj1, Object obj2) {
          return ((AppData)obj1).match_rating - ((AppData)obj2).match_rating;
        }
      });
    }

    return app_list;
  }

  void convertCursorToAppData(Cursor cursor, Map<String, AppData> app_map) {
    boolean result = cursor.moveToFirst();
    while (result && !isCancelled()) {
      String app_name = cursor.getString(0);
      Log.d("MostUsed", "Got data: " + app_name + "," + cursor.getString(1) + "," + cursor.getString(2));
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
      result = cursor.moveToNext();
    }
  }

  /** When done, populate the results list. This is done on the GUI thread,
   *  as mandated by Android (GUI operations don't like multithreading).
   */
  protected void onPostExecute(ArrayList<AppData> app_list) {
    AppArrayAdapter adapter = new AppArrayAdapter(m_parent_activity, R.id.resultsListView, app_list);
    ListView results_list_view = (ListView)m_parent_activity.findViewById(R.id.resultsListView);
    results_list_view.setAdapter(adapter);
  }
}
