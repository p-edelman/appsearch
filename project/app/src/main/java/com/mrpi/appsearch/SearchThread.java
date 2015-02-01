package com.mrpi.appsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

/** Perform a fuzzy match on the app cache in the background and populate the
 *  results list. The search may be cancelled if the user refines the query
 *  before the results are ready.
 *  <p>
 *  A fuzzy match means that all app names on the device are found where all
 *  letters of the query are present in the presented order, but not necessarily
 *  adjacent to each other.
 *  <p>
 *  The results are sorted in such a way that apps starting with the query are
 *  presented first. The more characters between the query characters, the lower
 *  on the list the app will be. 
 */
public class SearchThread extends AsyncTask<Object, Void, ArrayList<AppData>> {

  private Context m_context;
  private SearchThreadListener m_listener;

  /** Instantiate the class.
   * @param context an application context where this class can run in (and
   *                that provides access to the private database of tha app).
   * @param listener a SearchThreadListener instance whose
   *                 onSearchThreadFinished() method is called upon completion
   *                 with the result of the search as a parameter. */
  public SearchThread(Context context,
                      SearchThreadListener listener) {
    m_context  = context;
    m_listener = listener;
  }

  /** Perform everything that needs to be done in the background: performing the
   *  search and sorting the results.
   *  @param query_param an array of query string, of which only the first one
   *         is used.
   */
  @Override
  protected ArrayList<AppData> doInBackground(Object... query_param) {
    final String query = ((String)query_param[0]).toLowerCase();
    
    // Query the database for all app names that have the characters in our
    // query in the proper order, although not necessary adjacent to each
    // other.
    SQLiteDatabase cache = AppCacheOpenHelper.getInstance(m_context).getReadableDatabase();
    String db_query = "%";
    for (int pos = 0; pos < query.length(); pos++) db_query += query.charAt(pos) + "%";
    Cursor cursor = cache.rawQuery("SELECT DISTINCT public_name, package_name FROM " + AppCacheOpenHelper.TBL_APPS + " WHERE public_name LIKE ?",  new String[]{db_query});

    // Put the results in a list of AppData.
    final ArrayList<AppData> app_list = new ArrayList<AppData>();
    boolean result = cursor.moveToFirst();
    while (result && !isCancelled()) {
      AppData app_data = new AppData();
      app_data.name = cursor.getString(0);
      app_data = getMatchRating(app_data, query);
      app_data.package_name = cursor.getString(1);
      app_list.add(app_data);
      result = cursor.moveToNext();
    }
    Log.d("AppSearch", "Found " + cursor.getCount() + " results");

    // Sort by comparing the ratings
    if (!isCancelled()) {
      Collections.sort(app_list, new Comparator<Object>() {
        public int compare(Object obj1, Object obj2) {
          return ((AppData)obj1).match_rating - ((AppData)obj2).match_rating;
        }
      });
    }

    return app_list;
  }

  /** When done, communicate the results back to the caller by calling its
   *  onSearchThreadFinished() method. */
  protected void onPostExecute(ArrayList<AppData> apps) {
    m_listener.onSearchThreadFinished(apps, m_context);
  }

  /**
   * Calculate the "amount of match" between query and string. The lower the
   * number, the better the query is contained in the name. Along the way, mark
   * the characters that match to the query.
   * <p>
   * The lower the match rating, the better the match. -1 means the query is the
   * app name. If the query is contained is the name, the rating is the number
   * of characters in front of it. If the query letters are spread out over the
   * name, there's a penalty of 100 plus the number of characters in between.
   * <p>
   * Search is done case insensitive. 
   * @param app_data an AppData object that should have its .name parameter set.
   *        It is assumed that all letters of the query are present in the same
   *        order in the name, although not necessarily adjacent to each other.
   * @param query a short string of characters to match against the name.
   * @return an AppData object with its .match_rating and .char_matches
   * parameters set.
   */
  private AppData getMatchRating(AppData app_data, String query) {
    app_data.char_matches = new ArrayList<Integer>();  
    String name = app_data.name.toLowerCase(Locale.US);
    
    int index = name.indexOf(query);
    if (index != -1) {
      if ((index == 0) && (query.length() == name.length())) {
        app_data.match_rating = -1; // Query is app name; we're golden!
      } else {
        app_data.match_rating = index; // Rating is the number of chars in front
                                       // of the query.
      }
      // Mark the matching characters
      for (int i = index; i < (index + query.length()); i++) {
        app_data.char_matches.add(i);
      }
    } else {
      int rating = 100; // Query is not contained as whole in app name, which
                        // means results should sink to the bottom. Therefore
                        // the rating gets a penalty of 100.
      int name_pos = -1;
      char query_char, name_char;
      for (int query_pos = 0; query_pos < query.length(); query_pos++) { 
        name_pos += 1;
        query_char = query.charAt(query_pos);
        name_char  = name.charAt(name_pos);
        while (name_char != query_char) {
          rating   += 1;
          name_pos += 1;
          name_char = name.charAt(name_pos);
        }
        app_data.char_matches.add(name_pos);
      }
      app_data.match_rating = rating;
    }
    return app_data;
  }
}
