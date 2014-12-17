package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Log;

public class FindMostUsedThread extends AsyncTask<String, Void, ArrayList<AppData>> {

  // We need to remember the parent activity so that we can find the GUI element
  // for the results list.
  private Activity m_parent_activity;
  
  public FindMostUsedThread (Activity parent_activity) {
    this.m_parent_activity = parent_activity;
  }
  
  @Override
  protected ArrayList<AppData> doInBackground(String... params) {
    final ArrayList<AppData> app_list = new ArrayList<AppData>();

    SQLiteDatabase cache;
    try {
      cache = AppCacheOpenHelper.getInstance(m_parent_activity).getReadableDatabase();
    } catch (SQLiteException e) {
      return app_list;
    }
    
    String slot_str = Long.toString(AppCacheOpenHelper.getTimeSlot());
    Cursor cursor = cache.rawQuery("SELECT DISTINCT public_name, package_name, count, day FROM usage WHERE time_slot=? ORDER BY count DESC",
                                   new String[]{slot_str});

    int day  = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

    // Put the results in a list of AppData.
    boolean result = cursor.moveToFirst();
    while (result && !isCancelled()) {
      AppData app_data = new AppData();
      app_data.name = cursor.getString(0);
      if (cursor.getInt(2) == day) { 
        app_data.match_rating = cursor.getInt(1) * 7;
      } else {
        app_data.match_rating = cursor.getInt(1);
      }
      app_data.package_name = cursor.getString(1);
      app_list.add(app_data);
      result = cursor.moveToNext();
    }
    Log.d("AppSearch", "Found " + cursor.getCount() + " results");
    
    // Sort by comparing the two ratings
    if (!isCancelled()) {
      Collections.sort(app_list, new Comparator<Object>() {
        public int compare(Object obj1, Object obj2) {
          return ((AppData)obj1).match_rating - ((AppData)obj2).match_rating;
        }
      });
    }

    return app_list;
  }

}
