package com.mrpi.appsearch;

import android.content.Context;
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
 */
public class AppCacheOpenHelper extends SQLiteOpenHelper {

  // The only instance, needed for the singleton mechanism
  private static AppCacheOpenHelper m_instance;

  /** Housekeeping parameters */
  private static final int    DB_VERSION = 2;
  private static final String DB_NAME    = "apps.sqlite";

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

  /** Remove an app from the database.
    * @param package_name the package name of the app to be removed.
    */
  public void removePackage(String package_name) {
    SQLiteDatabase db = getWritableDatabase();
    String[] where_args = {package_name};
    db.delete(TBL_USAGE_ALL, "package_name=?", where_args);
    db.delete(TBL_USAGE_DAY, "package_name=?", where_args);
    db.delete(TBL_USAGE_WEEK, "package_name=?", where_args);
  }
}
