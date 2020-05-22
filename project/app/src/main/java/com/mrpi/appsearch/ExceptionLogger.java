package com.mrpi.appsearch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Logger for unhandled Exceptions. It will save all stacktraces timestamped to the database, and
 * then pass on the exception to the original default UncaughtExceptionHandler.
 *
 * It should be installed with Thread.setDefaultUncaughtExceptionHandler()
 *
 * Stacktraces older than 60 days will be deleted, although this action is only done when this class
 * is invoked somehow, i.e. when an Exception occurs or a report is generated. However, it is
 * unlikely that the stacktraces table will ever grow so large that there is a real need to purge
 * it.
 */
public class ExceptionLogger implements Thread.UncaughtExceptionHandler {
    private SQLiteDatabase m_db;
    private Thread.UncaughtExceptionHandler m_default_exception_handler = Thread.getDefaultUncaughtExceptionHandler();

    public ExceptionLogger(Context context) {
        m_db = DBHelper.getInstance(context).getWritableDatabase();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Format the stacktrace as a text string
        Writer result = new StringWriter();
        PrintWriter print_writer = new PrintWriter(result);
        throwable.printStackTrace(print_writer);
        String stacktrace = result.toString();
        print_writer.close();

        // Put it in the database
        ContentValues values = new ContentValues();
        values.put("stacktrace", stacktrace);
        m_db.insert(DBHelper.TBL_STACKTRACES, null, values);

        // The aim is to save only the last 60 days of stacktraces, so let's do some cleaning up
        // while we're here
        deleteOlderThan60Days();

        // Re-emit the exception, because we didn't really handle it
        m_default_exception_handler.uncaughtException(thread, throwable);
    }

    /**
     * Get a nicely formatted report of all stacktraces for the last 60 days.
     *
     * @return err, a nicely formatted report of all stacktraces for the last 60 days.
     */
    public String getFormattedStackTraces() {
        deleteOlderThan60Days();

        String output = "These are all the stacktraces for the last 60 days:\n";
        Cursor cursor = m_db.query(DBHelper.TBL_STACKTRACES, null, null, null, null, null, "datetime(timestamp) ASC", null);
        boolean has_result = cursor.moveToFirst();
        while (has_result) {
            output += "On " + cursor.getString(0) + ":\n" + cursor.getString(1) + "\n\n";
            has_result = cursor.moveToNext();
        }
        return output;
    }

    /**
     * Delete all stacktraces older than 60 days.
     */
    private void deleteOlderThan60Days() {
        m_db.delete(DBHelper.TBL_STACKTRACES, "datetime(timestamp) < datetime('now', '-60 days')", null);
    }
}
