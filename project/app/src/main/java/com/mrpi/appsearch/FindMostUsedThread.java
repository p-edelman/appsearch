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

    SQLiteDatabase cache;
    try {
      cache = AppCacheOpenHelper.getInstance(m_parent_activity).getReadableDatabase();
    } catch (SQLiteDatabaseLockedException e) {
      // TODO: Handle this properly
      Log.d("AppSearch", "Can't get a lock on the database!");
      return app_list;
    }

    int day  = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    String day_str  = Integer.toString(day);
    String slot_str = Long.toString(AppCacheOpenHelper.getTimeSlot());

    // Get all apps for this time slot
    String sql_query = "SELECT DISTINCT public_name, package_name, day, count " +
                       "FROM usage" +
                       "WHERE time_slot=?" +
                       "ORDER BY count DESC";
    Cursor cursor = cache.rawQuery(sql_query, new String[]{slot_str, day_str});

    // Create an entry for each app with the score calculated by clicks and
    // day.
    Map<String, AppData> app_map = new HashMap<String, AppData>();
    boolean result = cursor.moveToFirst();
    while (result && !isCancelled()) {
      String app_name = cursor.getString(0);
      Log.d("AppSearch", "Found match on " + app_name);
      AppData app_data = app_map.get(app_name);
      if (app_data == null) {
        app_data = new AppData();
        app_map.put(app_name, app_data);
      }
      app_data.name         = cursor.getString(0);
      app_data.package_name = cursor.getString(1);
      if (cursor.getInt(2) == day) {
        // There is a match on this particular day, so the match gets a boost
        app_data.match_rating += cursor.getInt(3) * 7;
      } else {
        app_data.match_rating += cursor.getInt(3);
      }
      result = cursor.moveToNext();
    }
    cursor.close();

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

  /** When done, populate the results list. This is done on the GUI thread,
   *  as mandated by Android (GUI operations don't like multithreading).
   */
  protected void onPostExecute(ArrayList<AppData> app_list) {
    AppArrayAdapter adapter = new AppArrayAdapter(m_parent_activity, R.id.resultsListView, app_list);
    ListView results_list_view = (ListView)m_parent_activity.findViewById(R.id.resultsListView);
    results_list_view.setAdapter(adapter);
  }
}
