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
  private static final int    DB_VERSION = 1;
  private static final String DB_NAME    = "apps";
  private static final String SCHEMA     = "(public_name TEXT PRIMARY KEY, package_name TEXT)";

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
    db.execSQL("CREATE TABLE apps " + SCHEMA + ";");       
    db.execSQL("CREATE TABLE dirty " + SCHEMA + ";");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

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
    db.execSQL("CREATE TABLE dirty " + SCHEMA + ";");
    db.setTransactionSuccessful();
    db.endTransaction();
    Log.d("AppSearch", "Switch made");
  }
}
