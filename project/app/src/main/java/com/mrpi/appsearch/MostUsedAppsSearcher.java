package com.mrpi.appsearch;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Class for finding the most used apps for the current moment.
 * This thread gets the results from the cached database, but filters out any
 * apps that are not present any more on the system; they are not included in
 * the result.
 */
public class MostUsedAppsSearcher {

    private Context        m_context;
    private PackageManager m_package_manager;
    private int            m_max_results;

    /**
     * Simple constructor.
     *
     * @param context Android Context this app operates in.
     */
    public MostUsedAppsSearcher(Context context) {
        this(context, -1);
    }

    /**
     * Simple constructor.
     *
     * @param context Android Context this app operates in.
     * @param max_results the maximum number of results returned from a search. By default, all apps
     *                    are returned.
     */
    public MostUsedAppsSearcher(Context context, int max_results) {
        m_context         = context;
        m_package_manager = context.getPackageManager();
        m_max_results     = max_results;
    }

    /**
     * Query the database to find the most used apps.
     */
    public ArrayList<FuzzyAppSearchResult> search() {
        // Our return object
        ArrayList<FuzzyAppSearchResult> apps = new ArrayList<FuzzyAppSearchResult>();

        // Open the database
        SQLiteDatabase db = null;
        try {
            db = DBHelper.getInstance(m_context).getReadableDatabase();
        } catch (SQLiteDatabaseLockedException se) {
            // TODO: Handle this properly
            Log.d("AppSearch", "Can't get a lock on the database!");
        }

        if (db != null) {
            // Get the top apps for this time and day or overall. Since apps might
            // occur twice in this list (one for time slot and day, and one overall),
            // we need to set the limit to double the requested number.
            String time_slot_str = Long.toString(CountAndDecay.getTimeSlot());
            String day_str = Integer.toString(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
            String limit_str = (m_max_results > 0) ? Integer.toString(2 * m_max_results) : "";
            Cursor cursor = db.query(DBHelper.TBL_USAGE,
                    new String[]{"package_name", "score"},
                    "(time_slot=? AND day=?) OR (time_slot=-1 AND day=-1)",
                    new String[]{time_slot_str, day_str},
                    null, null,
                    "score DESC", limit_str);

            // Process the results, but stop if we have enough data
            boolean result = cursor.moveToFirst();
            while (result && (apps.size() < m_max_results || m_max_results == -1)) {
                String package_name = cursor.getString(0);
                Intent intent = m_context.getPackageManager().getLaunchIntentForPackage(package_name);
                if (intent != null) { // Intent will be null if package has been uninstalled, so we filter out these apps here
                    ActivityInfo activity_info = intent.resolveActivityInfo(m_package_manager, 0);
                    String name = activity_info.loadLabel(m_package_manager).toString();
                    FuzzyAppSearchResult app_data = new FuzzyAppSearchResult(name, package_name);

                    // If the package is already present in the list, this new entry has a
                    // lower score so we can ignore it.
                    if (!apps.contains(app_data)) {
                        app_data.match_rating = cursor.getInt(1);
                        apps.add(app_data);
                    }
                }
                result = cursor.moveToNext();
            }
            cursor.close();
        }

        return apps;
    }
}
