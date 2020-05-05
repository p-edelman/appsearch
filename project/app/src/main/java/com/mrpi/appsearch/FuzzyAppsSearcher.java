package com.mrpi.appsearch;

import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Fuzzy searcher (see base class for an explanation) targeted towards apps.
 */
public class FuzzyAppsSearcher extends FuzzySearcher<AppSearchResult> {

    public FuzzyAppsSearcher(Context context) {
        super(context);
    }

    protected ArrayList<AppSearchResult> queryDB(SQLiteDatabase db, String query) {
        // Query the database for all app names that have the characters in our query in the proper
        // order, although not necessary adjacent to each other.
        // The database is pre-sorted on app popularity (for this moment, if all goes well).
        Cursor cursor = db.rawQuery("SELECT DISTINCT public_name, package_name FROM " + DBHelper.TBL_APPS + " WHERE public_name LIKE ? ORDER BY ROWID",
                new String[]{formatQueryForDB(query)});

        // Put the results in a list.
        final ArrayList<AppSearchResult> apps = new ArrayList<>();
        boolean result = cursor.moveToFirst();
        while (result) {
            AppSearchResult app_data = new AppSearchResult(cursor.getString(0), cursor.getString(1));
            apps.add(app_data);
            result = cursor.moveToNext();
        }
        Log.d("AppSearch", "Found " + cursor.getCount() + " results");

        return apps;
    }
}
