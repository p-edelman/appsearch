package com.mrpi.appsearch;
import java.util.Calendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/** Provider for the app cache database.
 *  <p>
 *  This class is written as a singleton; during the lifecycle of the app only a
 *  single instance exists (accessible by the class method 
 *  {@link #getInstance(Context)}) to share a single database connection with
 *  all components and threads.
 *  <p>
 *  Warning: the database connection may <b>never</b> be closed!
 *  <p>
 *  The database uses a table called "apps" that can be used for searching, and
 *  a shadow table called "dirty" to build a new index. The method
 *  {@link #switchDirty()} needs to be called when indexing is complete to
 *  make the "dirty" table the new "apps" table and build a new empty dirty
 *  table.  
 */
public class AppCacheOpenHelper extends SQLiteOpenHelper {

  // The only instance, needed for the singleton mechanism
  private static AppCacheOpenHelper m_instance;
  
  // Housekeeping parameters
  private static final int    DB_VERSION = 2;
  private static final String DB_NAME    = "apps";
  
  /** The schema for the table with installed apps. */
  private static final String SCHEMA_INSTALLED = "(public_name TEXT PRIMARY KEY, package_name TEXT)";
  
  /** The schema for the table with the app usage. */
  private static final String SCHEMA_USAGE = "(public_name TEXT, package_name TEXT, day INTEGER, time_slot INTEGER, count INTEGER)";
  
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
    db.execSQL("CREATE TABLE apps " + SCHEMA_INSTALLED + ";");       
    db.execSQL("CREATE TABLE dirty " + SCHEMA_INSTALLED + ";");
    db.execSQL("CREATE TABLE usage " + SCHEMA_USAGE + ";");
  }
  
  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE usage;");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old_version, int new_version) {
    Log.d("AppSearch", "New version: " + new_version);
    if ((old_version == 1) && (new_version == 2)) {
      db.beginTransaction();
      db.execSQL("DROP TABLE usage;");
      db.execSQL("CREATE TABLE usage " + SCHEMA_USAGE + ";");
      db.setTransactionSuccessful();
      db.endTransaction();
      Log.d("AppSearch", "Database upgraded to version 2");
    }
  }

  /** After the database has been filled with updated app data, the old "app"
   *  table is switched out for the new one, and a new empty "dirty" table is 
   *  provided.
   */
  public void switchDirty() {
    Log.d("AppSearch", "Making the switch");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    db.execSQL("DROP TABLE apps;");
    db.execSQL("ALTER TABLE dirty RENAME TO apps;");
    db.execSQL("CREATE TABLE dirty " + SCHEMA_INSTALLED + ";");
    db.setTransactionSuccessful();
    db.endTransaction();
    Log.d("AppSearch", "Switch made");
  }
  
  public static long getTimeSlot() {
    Calendar now = Calendar.getInstance();
    long slot = (now.get(Calendar.HOUR_OF_DAY) * 12) +
                Math.round((double)now.get(Calendar.MINUTE) / 5.0);
    return slot;
  }
  
  public void countAppLaunch(String public_name, String package_name) {
    SQLiteDatabase db = getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put("public_name",  public_name);
    values.put("package_name", package_name);

    int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    String day_str = Integer.toString(day);
    values.put("day", day_str);
    
    long slot = getTimeSlot();

    int adjacent = 5;
    while (adjacent > -6) {
      long tmp_slot = slot + adjacent;
      if (tmp_slot < 0) {
        // Time stamp was before midnight
        tmp_slot = (12 * 24) + tmp_slot;
      }
      if (tmp_slot == ((12 * 24) - 1)) {
        // Go back one day
        day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        day_str = Integer.toString(day);
        values.put("day", day_str);       
      }
      String slot_str = Long.toString(tmp_slot);
      values.put("time_slot", slot_str);

      // The time slots further away get progressively smaller bonuses
      int bonus = (5 - (Math.abs(adjacent)));

      // See if we already have records for this date and time
      Cursor cursor = db.rawQuery("SELECT count FROM usage WHERE public_name=? AND day=? AND time_slot=?;",
                                  new String[]{public_name, day_str, slot_str});
      if (cursor.moveToFirst()) {
        values.put("count", cursor.getInt(0) + bonus);
        db.replace("usage", null, values);
        Log.d("AppSearch", "Updated slot " + slot_str + " with count " + cursor.getInt(0) + bonus);
      } else {
        values.put("count", bonus);
        db.insert("usage", null, values);
        Log.d("AppSearch", "Inserted slot " + slot_str + " with count " + bonus);
      }
      adjacent--;
    }
  }
}
