package com.mrpi.appsearch;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
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
    * @param params the number of results required as integer. If this is not given, all apps are
   *                returned. Note that the result is not guaranteed to be a long as given, as there
   *                might not be enough apps in the database. */
  @Override
  protected ArrayList<AppData> doInBackground(Object... params) {
    int num_results = 0;
    try {
      num_results = (Integer)params[0];
    } catch (ArrayIndexOutOfBoundsException ae) {} // Number of results is unlimited

    // Our return object
    ArrayList<AppData> apps = null;

    // Open the database
    SQLiteDatabase db = null;
    try {
      db = DBHelper.getInstance(m_context).getReadableDatabase();
    } catch (SQLiteDatabaseLockedException se) {
      // TODO: Handle this properly
      Log.d("AppSearch", "Can't get a lock on the database!");
    }

    if (db != null) {
      // Create an entry for each app to keep it unique
      Map<String, AppData> app_map = new TreeMap<String, AppData>();

      // Get the top apps for this time and day or overall. Since apps might occur twice in this
      // list (one for time slot and day, and one overall), we need to set the limit to double the
      // requested number.
      String time_slot_str = Long.toString(CountAndDecay.getTimeSlot());
      String day_str       = Integer.toString(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
      String limit_str     = (num_results > 0) ? Integer.toString(2 * num_results) : "";
      Cursor cursor = db.query(DBHelper.TBL_USAGE,
              new String[]{"package_name", "score"},
              "(time_slot=? AND day=?) OR (time_slot=-1 AND day=-1)",
              new String[]{time_slot_str, day_str},
              null, null,
              "score DESC", limit_str);
      boolean result = cursor.moveToFirst();
      while (result && !isCancelled() &&
             (app_map.size() < num_results || num_results == 0)) {
        String package_name = cursor.getString(0);
        // If the package is already present in the list, this new entry has a lower score so we can
        // ignore it.
        if (!app_map.containsKey(package_name)) {
          AppData app_data = new AppData(package_name);
          app_data.match_rating = cursor.getInt(1);
          app_map.put(package_name, app_data);
          Log.d("SearchMostUsedThread", "Rating for " + app_data.name + " is " + app_data.match_rating);
        }
        result = cursor.moveToNext();
      }
      cursor.close();

      // Generate an ArrayList with the sorted apps.
      if (!isCancelled()) apps = new ArrayList<AppData>(app_map.values());
    }

    return apps;
  }
}
