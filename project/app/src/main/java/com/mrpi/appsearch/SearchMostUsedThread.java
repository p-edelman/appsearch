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
 * Class for finding the most used apps in a background thread.
 * This thread gets the results from the cached database, but filters out any
 * apps that are not present any more on the system; they are not included in
 * the result.
 */
public class SearchMostUsedThread extends SearchThread {

    private PackageManager m_package_manager;

    public SearchMostUsedThread(Context context, SearchThreadListener listener) {
        super(context, listener);
        m_package_manager = context.getPackageManager();
    }

    /**
     * Query the database to find the most used apps.
     *
     * @param params the number of results required as integer. If this is not
     *               given, all apps are returned.
     *               Note that the result is not guaranteed to be a long as
     *               given, as there might not be enough apps in the database.
     */
    @Override
    protected ArrayList<AppData> doInBackground(Object... params) {
        int num_results = 0;
        try {
            num_results = (Integer) params[0];
        } catch (ArrayIndexOutOfBoundsException ae) {
        } // Number of results is unlimited

        // Our return object
        ArrayList<AppData> apps = new ArrayList<AppData>();

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
            String limit_str = (num_results > 0) ? Integer.toString(2 * num_results) : "";
            Cursor cursor = db.query(DBHelper.TBL_USAGE,
                    new String[]{"package_name", "score"},
                    "(time_slot=? AND day=?) OR (time_slot=-1 AND day=-1)",
                    new String[]{time_slot_str, day_str},
                    null, null,
                    "score DESC", limit_str);

            // Process the results, but stop if we have enough data or if this thread
            // is cancelled.
            boolean result = cursor.moveToFirst();
            while (result && !isCancelled() &&
                    (apps.size() < num_results || num_results == 0)) {
                String package_name = cursor.getString(0);
                Intent intent = m_context.getPackageManager().getLaunchIntentForPackage(package_name);
                if (intent != null) { // Intent will be null if package has been uninstalled, so we filter out these apps here
                    ActivityInfo activity_info = intent.resolveActivityInfo(m_package_manager, 0);
                    String name = activity_info.loadLabel(m_package_manager).toString();
                    AppData app_data = new AppData(name, package_name);

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

        if (!isCancelled()) {
            return apps;
        }
        return null;
    }
}
