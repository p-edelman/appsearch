package com.mrpi.appsearch;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** The model to track and score the app opens.
 *  <p>
 *  The model uses a mechanism where each app opening is assigned an absolute
 *  score, and this score decays at a constant rate each day. By using a
 *  (fractional) decay, scores cannot grow too large and app opening that are no
 *  longer refreshed get flushed out of the database fairly quickly.
 *  <p>
 *  The score is kept for three different situations: the score for this time
 *  on the day of the week, the score for this time/day combination, and the
 *  score regardless of time and day. These scores are kept in three different
 *  database tabels. The values are chosen such that an app opening for this
 *  time and day is weighted heaviest, then an opening for this time and then an
 *  opening overall. Approximately, two consecutive openings of an app on a
 *  given day and time outweigh five openings on that time only, and only ten
 *  overall openings outweigh that.
 *  <p>
 *  Time is counted in five minute slots over the day, which can be retrieved
 *  with {@link #getTimeSlot()}. An app launch is thus logged in five minute
 *  precision intervals. In addition, an app launch spills over to the ten
 *  adjacent (five on both sides) slots with progressively smaller scores.
 */
public class CountAndDecay {
  /** The score values for each situation */
  public static final int SCORE_DAY  = 100;
  public static final int SCORE_WEEK = 300;
  public static final int SCORE_ALL  = 10;

  /** The daily decay rate for the bonus values. */
  public static final double DECAY_RATE = 0.1;

  private AppCacheOpenHelper m_cache;

  public CountAndDecay(AppCacheOpenHelper cache) {
    m_cache = cache;
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

  /** Record the launch of an app in the database.
   *  <p>
   *  This method sets or updates the count field of the three tables to the
   *  proper value for the current time slot and the surrounding time slots.
   *  @param public_name the public name of the app
   *  @param package_name the package name of the app
   */
  public void countAppLaunch(String public_name, String package_name) {
    // Perform a decay step if needed
    decay();

    // Update the overall table
    SQLiteDatabase db = m_cache.getWritableDatabase();
    SQLiteStatement all_statement = db.compileStatement(
      "REPLACE INTO " + AppCacheOpenHelper.TBL_USAGE_ALL + " (public_name, package_name, count) VALUES (" +
        "?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + AppCacheOpenHelper.TBL_USAGE_ALL + " WHERE public_name=?" +
        "), 0) + ?)");
    all_statement.bindString(1, public_name);
    all_statement.bindString(2, package_name);
    all_statement.bindString(3, public_name);
    all_statement.bindLong(4, SCORE_ALL);
    all_statement.executeInsert();

    // The SQL statement for the daily usage field
    SQLiteStatement day_statement = db.compileStatement(
      "REPLACE INTO " + AppCacheOpenHelper.TBL_USAGE_DAY + " (public_name, package_name, time_slot, count) VALUES (" +
        "?, ?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + AppCacheOpenHelper.TBL_USAGE_DAY + " WHERE public_name=? AND time_slot=?" +
        "), 0) + ?)");
    day_statement.bindString(1, public_name);
    day_statement.bindString(2, package_name);
    day_statement.bindString(4, public_name);

    // The SQL statement for the weekly usage field
    SQLiteStatement week_statement = db.compileStatement(
      "REPLACE INTO " + AppCacheOpenHelper.TBL_USAGE_WEEK + " (public_name, package_name, time_slot, day, count) VALUES (" +
        "?, ?, ?, ?, " +
        "COALESCE((" +
          "SELECT count FROM " + AppCacheOpenHelper.TBL_USAGE_WEEK + " WHERE public_name=? AND time_slot=? AND day=?" +
        "), 0) + ?)");
    week_statement.bindString(1, public_name);
    week_statement.bindString(2, package_name);
    week_statement.bindString(5, public_name);

    long slot = getTimeSlot();
    int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

    // Write the current and surrounding time slots to the daily and weekly
    // usage tables
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
      day_statement.bindLong(6, SCORE_DAY - ((Math.abs(adjacent) * 5)));
      day_statement.executeInsert();
      week_statement.bindLong(8, SCORE_WEEK - ((Math.abs(adjacent) * 5)));
      week_statement.executeInsert();

      adjacent--;
    }
    Log.d("AppSearch", "Logged the launch");
  }

  /** Decay the click counts in the database for the number of days since the
   *  last decay. */
  public void decay() {
    // Get the number of days to decay
    int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    int days_to_decay = getDaysSinceLastDecay(today);

    if (days_to_decay > 0) {
      Log.d("AppSearch", "Decaying " + days_to_decay + " days");
      SQLiteDatabase db = m_cache.getWritableDatabase();
      db.beginTransaction();
      for (int day = 0; day < days_to_decay; day++) {
        // Decay all values by 10 percent
        db.execSQL("UPDATE " + AppCacheOpenHelper.TBL_USAGE_ALL  + " SET count = round(count * 0.9)");
        db.execSQL("UPDATE " + AppCacheOpenHelper.TBL_USAGE_WEEK + " SET count = round(count * 0.9)");
        db.execSQL("UPDATE " + AppCacheOpenHelper.TBL_USAGE_DAY  + " SET count = round(count * 0.9)");

        // Delete all entries that fall below 6 (they cannot decay any further).
        // This happens after a little bit less than a month for an app that hasn't
        // been clicked anymore.
        db.delete(AppCacheOpenHelper.TBL_USAGE_ALL,  "count < 6", null);
        db.delete(AppCacheOpenHelper.TBL_USAGE_WEEK, "count < 6", null);
        db.delete(AppCacheOpenHelper.TBL_USAGE_DAY,  "count < 6", null);
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
    SQLiteDatabase db = m_cache.getReadableDatabase();
    Cursor cursor = db.query("metadata",
            new String[]{"content"},
            "field='last_decay'",
            null, null, null, null);
    boolean success = cursor.moveToFirst();
    if (success) {
      int last_decay = cursor.getInt(0);
      days_to_decay = to_when - last_decay;
      Log.d("Decay", "Decaying " + days_to_decay + "days");
      if (days_to_decay < 0) {
        // Happy new year!
        Calendar today_date      = Calendar.getInstance();
        Calendar last_decay_date = Calendar.getInstance();
        last_decay_date.set(Calendar.DAY_OF_YEAR, last_decay);
        long today_ms      = today_date.getTimeInMillis();
        long last_decay_ms = last_decay_date.getTimeInMillis();
        days_to_decay = (int) TimeUnit.MILLISECONDS.toDays(today_ms - last_decay_ms);
      }
    } else {
      // There wasn't a timestamp in the database, so create one for today
      ContentValues values = new ContentValues();
      values.put("field", "last_decay");
      values.put("content", Calendar.getInstance().get(Calendar.DAY_OF_YEAR));
      db = m_cache.getWritableDatabase();
      db.insert("metadata", null, values);
    }
    cursor.close();
    return days_to_decay;
  }
}
