package com.mrpi.appsearch;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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

  /** Housekeeping parameters */
  private static final int    DB_VERSION = 2;
  private static final String DB_NAME    = "apps.sqlite";

  /** The bonus values for each table */
  public static final int BONUS_DAY  = 100;
  public static final int BONUS_WEEK = 300;
  public static final int BONUS_ALL  = 10;

  /** The daily decay rate for the bonus values. */
  public static final double DECAY_RATE = 0.1;

  /** The schema for the tables with the app usage. */
  public static final String TBL_USAGE_ALL     = "usage_all";
  public static final String TBL_USAGE_DAY     = "usage_day";
  public static final String TBL_USAGE_WEEK    = "usage_week";
  private static final String SCHEMA_USAGE_ALL  =
    "(public_name TEXT, package_name TEXT, count INTEGER, PRIMARY KEY (public_name))";
  private static final String SCHEMA_USAGE_DAY  =
    "(public_name TEXT, package_name TEXT, time_slot INTEGER, count INTEGER, PRIMARY KEY (public_name, time_slot))";
  private static final String SCHEMA_USAGE_WEEK =
    "(public_name TEXT, package_name TEXT, day INTEGER, time_slot INTEGER, count INTEGER, PRIMARY KEY (public_name, day, time_slot))";

  /** The metadata table is a simple text key/numeric value storage. */
  private static final String SCHEMA_METADATA = "(field TEXT PRIMARY KEY, content INTEGER)";

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
    db.beginTransaction();
    db.execSQL("CREATE TABLE " + TBL_USAGE_ALL + " " + SCHEMA_USAGE_ALL);
    db.execSQL("CREATE TABLE " + TBL_USAGE_DAY + " " + SCHEMA_USAGE_DAY);
    db.execSQL("CREATE TABLE " + TBL_USAGE_WEEK + " " + SCHEMA_USAGE_WEEK);
    db.execSQL("CREATE TABLE metadata " + SCHEMA_METADATA);
    db.setTransactionSuccessful();
    db.endTransaction();
    Log.d("AppSearch", "Database initialized");
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
      db.execSQL("CREATE TABLE metadata " + SCHEMA_METADATA);
      db.setTransactionSuccessful();
      db.endTransaction();
      Log.d("AppSearch", "Database upgraded to version 2");
    }
  }

  /** Get the current five minute slot of the day.
   *  <p>
   *  App activity is logged in time slots of five minutes, so daily app launch
   *  at around the same time results in a count for the time slot every day. */
  public static long getTimeSlot() {
    Calendar now = Calendar.getInstance();
    long slot = (now.get(Calendar.HOUR_OF_DAY) * 12) +
                now.get(Calendar.MINUTE) / 5;
    return slot;
  }
  
  public void countAppLaunch(String public_name, String package_name) {
    decay();

    SQLiteDatabase db = getWritableDatabase();
    SQLiteStatement all_statement = db.compileStatement(
      "REPLACE INTO " + TBL_USAGE_ALL + " (public_name, package_name, count) VALUES (" +
        "?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + TBL_USAGE_ALL + " WHERE public_name=?" +
        "), 0) + ?)");
    all_statement.bindString(1, public_name);
    all_statement.bindString(2, package_name);
    all_statement.bindString(3, public_name);
    all_statement.bindLong(4, BONUS_ALL);
    all_statement.executeInsert();

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

      // Insert progressively smaller bonuses the further away we are from the
      // time slot
      day_statement.bindLong(6, BONUS_DAY - ((Math.abs(adjacent) * 5)));
      day_statement.executeInsert();
      week_statement.bindLong(8, BONUS_WEEK - ((Math.abs(adjacent) * 5)));
      week_statement.executeInsert();

      adjacent--;
    }
    Log.d("AppSearch", "Logged the launch");
  }

  /** Decay the click counts in the database for the number of days since the
   *  last decay.
   */
  public void decay() {
    // Get the number of days to decay
    int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    int days_to_decay = getDaysSinceLastDecay(today);

    if (days_to_decay > 0) {
      Log.d("AppSearch", "Decaying " + days_to_decay + " days");
      SQLiteDatabase db = getWritableDatabase();
      db.beginTransaction();
      for (int day = 0; day < days_to_decay; day++) {
        // Decay all values by 10 percent
        db.execSQL("UPDATE " + TBL_USAGE_ALL  + " SET count = round(count * 0.9)");
        db.execSQL("UPDATE " + TBL_USAGE_WEEK + " SET count = round(count * 0.9)");
        db.execSQL("UPDATE " + TBL_USAGE_DAY  + " SET count = round(count * 0.9)");

        // Delete all entries that fall below 6 (they cannot decay any further).
        // This happens after a little bit less than a month for an app that hasn't
        // been clicked anymore.
        db.delete(TBL_USAGE_ALL, "count < 6", null);
        db.delete(TBL_USAGE_WEEK, "count < 6", null);
        db.delete(TBL_USAGE_DAY, "count < 6", null);
      }

      // Write the decay day back to the database.
      ContentValues values = new ContentValues();
      values.put("field", "last_decay");
      values.put("content", today);
      db.replace("metadata", null, values);

      db.setTransactionSuccessful();
      db.endTransaction();
    }
  }

  /** Get the number of days since the last time the decay operation was
   *  performed.
   *  @param to_when the target day, expressed as a day number in the year.
   *  @return the number of days between the supplied day and the last decay
   *          operation according the database. */
  private int getDaysSinceLastDecay(int to_when) {
    int days_to_decay = 0;
    SQLiteDatabase db = getReadableDatabase();
    Cursor cursor = db.query("metadata",
                             new String[]{"content"},
                             "field='last_decay'",
                             null, null, null, null);
    boolean success = cursor.moveToFirst();
    Log.d("Decay", "Found a result in the db");
    if (success) {
      int last_decay = cursor.getInt(0);
      Log.d("Decay", "Last decay is " + last_decay);
      days_to_decay = to_when - last_decay;
      if (days_to_decay < 0) {
        // Happy new year!
        Calendar today_date      = Calendar.getInstance();
        Calendar last_decay_date = Calendar.getInstance();
        last_decay_date.set(Calendar.DAY_OF_YEAR, last_decay);
        long today_ms      = today_date.getTimeInMillis();
        long last_decay_ms = last_decay_date.getTimeInMillis();
        days_to_decay = (int)TimeUnit.MILLISECONDS.toDays(today_ms - last_decay_ms);
      }
    } else {
      // There wasn't a timestamp in the database, so create one for today
      ContentValues values = new ContentValues();
      values.put("field", "last_decay");
      values.put("content", Calendar.getInstance().get(Calendar.DAY_OF_YEAR));
      db = getWritableDatabase();
      db.insert("metadata", null, values);
    }
    cursor.close();
    return days_to_decay;
  }
}
