package com.mrpi.appsearch;
import java.util.Calendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

/** Provider for the app cache database.
 *  <p>
 *  This class is written as a singleton; during the lifecycle of the app only a
 *  single instance exists (accessible by the class method 
 *  {@link #getInstance(Context)}) to share a single database connection with
 *  all components and threads.
 *  <p>
 *  Warning: the database connection may <b>never</b> be closed!
 */
public class AppCacheOpenHelper extends SQLiteOpenHelper {

  // The only instance, needed for the singleton mechanism
  private static AppCacheOpenHelper m_instance;
  
  // Housekeeping parameters
  private static final int    DB_VERSION = 2;
  private static final String DB_NAME    = "apps";

  /** The schema for the tables with the app usage. */
  public static final String TBL_USAGE_ALL     = "usage_all";
  public static final String TBL_USAGE_DAY     = "usage_day";
  public static final String TBL_USAGE_WEEK    = "usage_week";
  public static final String SCHEMA_USAGE_ALL  =
    "(public_name TEXT, package_name TEXT, count INTEGER, PRIMARY KEY (public_name))";
  public static final String SCHEMA_USAGE_DAY  =
    "(public_name TEXT, package_name TEXT, time_slot INTEGER, count INTEGER, PRIMARY KEY (public_name, time_slot))";
  public static final String SCHEMA_USAGE_WEEK =
    "(public_name TEXT, package_name TEXT, day INTEGER, time_slot INTEGER, count INTEGER, PRIMARY KEY (public_name, day, time_slot))";

  private AppCacheOpenHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
  }
  
  /** Provide access to the single instance. */
  public static synchronized AppCacheOpenHelper getInstance(Context context) {
    if (m_instance == null) {
      m_instance = new AppCacheOpenHelper(context.getApplicationContext());
    }
    return m_instance;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + TBL_USAGE_ALL + " " + SCHEMA_USAGE_ALL + ";");
    db.execSQL("CREATE TABLE " + TBL_USAGE_DAY + " " + SCHEMA_USAGE_DAY + ";");
    db.execSQL("CREATE TABLE " + TBL_USAGE_WEEK + " " + SCHEMA_USAGE_WEEK + ";");
  }
  
  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    //db.execSQL("DROP TABLE " + TBL_USAGE_ALL + ";");
    //db.execSQL("DROP TABLE " + TBL_USAGE_DAY + ";");
    //db.execSQL("DROP TABLE " + TBL_USAGE_WEEK + ";");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old_version, int new_version) {
    Log.d("AppSearch", "New version: " + new_version);
    if ((old_version == 1) && (new_version == 2)) {
      db.beginTransaction();
      db.execSQL("DROP TABLE apps;");
      db.execSQL("DROP TABLE dirty;");
      db.execSQL("CREATE TABLE " + TBL_USAGE_ALL + " " + SCHEMA_USAGE_ALL + ";");
      db.execSQL("CREATE TABLE " + TBL_USAGE_DAY + " " + SCHEMA_USAGE_DAY + ";");
      db.execSQL("CREATE TABLE " + TBL_USAGE_WEEK + " " + SCHEMA_USAGE_WEEK + ";");
      db.setTransactionSuccessful();
      db.endTransaction();
      Log.d("AppSearch", "Database upgraded to version 2");
    }
  }

  public static long getTimeSlot() {
    Calendar now = Calendar.getInstance();
    long slot = (now.get(Calendar.HOUR_OF_DAY) * 12) +
                Math.round((double)now.get(Calendar.MINUTE) / 5.0);
    return slot;
  }
  
  public void countAppLaunch(String public_name, String package_name) {
    SQLiteDatabase db = getWritableDatabase();

    long result;

    SQLiteStatement all_statement = db.compileStatement(
      "REPLACE INTO " + TBL_USAGE_ALL + " (public_name, package_name, count) VALUES (" +
        "?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + TBL_USAGE_ALL + " WHERE public_name=?" +
        "), 0) + 1)");
    all_statement.bindString(1, public_name);
    all_statement.bindString(2, package_name);
    all_statement.bindString(3, public_name);
    result = all_statement.executeInsert();
    Log.d("CountLaunch", all_statement.toString());
    Log.d("CountLaunch", "Inserted overall value in row " + result);

    SQLiteStatement day_statement = db.compileStatement(
      "REPLACE INTO " + TBL_USAGE_DAY + " (public_name, package_name, time_slot, count) VALUES (" +
        "?, ?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + TBL_USAGE_DAY + " WHERE public_name=? AND time_slot=?" +
        "), 0) + ?)");
    day_statement.bindString(1, public_name);
    day_statement.bindString(2, package_name);
    day_statement.bindString(4, public_name);

    SQLiteStatement week_statement = db.compileStatement(
      "REPLACE INTO " + TBL_USAGE_WEEK + " (public_name, package_name, time_slot, day, count) VALUES (" +
        "?, ?, ?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + TBL_USAGE_WEEK + " WHERE public_name=? AND time_slot=? AND day=?" +
        "), 0) + ?)");
    week_statement.bindString(1, public_name);
    week_statement.bindString(2, package_name);
    week_statement.bindString(5, public_name);

    long slot = getTimeSlot();
    int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

    int adjacent = 5;
    while (adjacent > -6) {

      long tmp_slot = slot + adjacent;
      if (tmp_slot < 0) {
        // Time stamp was before midnight
        tmp_slot = (12 * 24) + tmp_slot;
      }
      if (tmp_slot == ((12 * 24) - 1)) {
        // Go back one day
        day -= 1;
        if (day == -1) day = 6;
      }
      day_statement.bindLong(3, tmp_slot);
      day_statement.bindLong(5, tmp_slot);
      week_statement.bindLong(3, tmp_slot);
      week_statement.bindLong(4, day);
      week_statement.bindLong(6, tmp_slot);
      week_statement.bindLong(7, day);

      // The time slots further away get progressively smaller bonuses
      long count = (5 - (Math.abs(adjacent)));
      day_statement.bindLong(6, count);
      week_statement.bindLong(8, count);

      result = day_statement.executeInsert();
      Log.d("CountLaunch", day_statement.toString());
      Log.d("CountLaunch", "Inserted day value in row " + result);

      week_statement.executeInsert();
      Log.d("CountLaunch", week_statement.toString());
      Log.d("CountLaunch", "Inserted week value in row " + result);

      adjacent--;
    }
    Log.d("AppSearch", "Logged the launch");
  }
}
