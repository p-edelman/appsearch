package com.mrpi.appsearch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/** Provider for the app database.
 *  <p>
 *  This class is written as a singleton; during the lifecycle of the app only a
 *  single instance exists (accessible by the class method 
 *  {@link #getInstance(Context)}) to share a single database connection with
 *  all components and threads.
 *  <p>
 *  Warning: the database connection may <b>never</b> be closed!
 *  <p>
 *  There are two classes of tables here. The first is a list of all the
 *  installed apps. The database uses two tables: a live table called "apps"
 *  that's ready on application start, and a table called "dirty" that can be
 *  used to write a new list to in the background. When indexing is complete,
 *  the method {@link #switchDirty()} makes the "dirty" table the new "apps"
 *  table and creates a new empty dirty table.
 *  <p>
 *  The second class is of tables contain the scores for the apps based on
 *  their number of launches. There are three tables: for launches on this
 *  particular time and day, on this particular time, and overall.
 */
public class DBHelper extends SQLiteOpenHelper {

  // The only instance, needed for the singleton mechanism
  private static DBHelper m_instance;

  /** Housekeeping parameters */
  private static final int    DB_VERSION = 2;
  private static final String DB_NAME    = "apps.sqlite";

  /** The schema for the table with installed apps. */
  public static final String TBL_APPS          = "apps";
  public static final String TBL_APPS_DIRTY    = "dirty";
  public static final String SCHEMA_INSTALLED  = "(package_name TEXT PRIMARY KEY, public_name TEXT)";

  /** The schema for the table with the app usage. */
  public  static final String TBL_USAGE    = "usage";
  private static final String SCHEMA_USAGE =
    "(package_name TEXT, day INTEGER, time_slot INTEGER, score INTEGER, PRIMARY KEY (package_name, day, time_slot))";

  /** The metadata table is a simple text key/numeric value storage. */
  private static final String SCHEMA_METADATA = "(field TEXT PRIMARY KEY, content INTEGER)";

  private DBHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
  }
  
  /** Provide access to the single instance. */
  public static synchronized DBHelper getInstance(Context context) {
    if (m_instance == null) {
      m_instance = new DBHelper(context.getApplicationContext());
    }
    return m_instance;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.beginTransaction();
    db.execSQL("CREATE TABLE " + TBL_APPS + " " + SCHEMA_INSTALLED + ";");
    db.execSQL("CREATE TABLE " + TBL_APPS_DIRTY + " " + SCHEMA_INSTALLED + ";");
    db.execSQL("CREATE TABLE " + TBL_USAGE + " " + SCHEMA_USAGE);
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
      db.execSQL("CREATE TABLE " + TBL_USAGE + " " + SCHEMA_USAGE + ";");
      db.execSQL("CREATE TABLE metadata " + SCHEMA_METADATA);
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
    db.beginTransactionNonExclusive();
    db.execSQL("DROP TABLE " + TBL_APPS + ";");
    db.execSQL("ALTER TABLE " + TBL_APPS_DIRTY + " RENAME TO " + TBL_APPS + ";");
    db.execSQL("CREATE TABLE " + TBL_APPS_DIRTY + " " + SCHEMA_INSTALLED + ";");
    db.setTransactionSuccessful();
    db.endTransaction();
    Log.d("AppSearch", "Switch made");
  }

  /** Remove an app from the database.
    * @param package_name the package name of the app to be removed.
    */
  public void removePackage(String package_name) {
    Log.d("AppSearch", "Removing package \"" + package_name + "\" from caches");
    SQLiteDatabase db = getWritableDatabase();
    String[] where_args = {package_name};
    db.delete(TBL_USAGE, "package_name=?", where_args);
  }
}
