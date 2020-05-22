package com.mrpi.appsearch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A ContentProvider that shares debug information about the app, which is the only thing this
 * app needs to share.
 *
 * There are two things this ContentProvider can share:
 * - the internal app database; using the path /export_db/AppSearch.sqlite
 * - stacktraces of the last 60 days; using the path /export_stacktraces/id/stacktraces.txt.
 *   id is a random 7-digit number; when stacktraces are requested, a new file is constructed, and
 *   since this request can be made from multiple entry's (query(), openFile(), getType()), we need
 *   a unique id to check if this is the same request as in the other entry or a new one, which is
 *   done using this 7-digit random number in the URI. Why 7 digits? Well, that's random.
 */
public class DebugContentProvider extends ContentProvider {

    /** The "authority" for content URI's for this app. */
    private static final String AUTHORITY = "org.mrpi.appsearch.provider";

    /** The 7-digit id for requesting stacktraces */
    private String m_stacktraces_uri_id;

    /**
     * The actions we recognize. Each action is accompanied by a URI path and a name for the file
     * that will be shared.
     */
    public enum Action {
        EXPORT_DB("export_db", "AppSearch.sqlite"),
        EXPORT_STACKTRACES("export_stacktraces", "stacktaces.txt");

        public final String path;
        public final String file_name;

        Action(String path, String file_name) {
            this.path      = path;
            this.file_name = file_name;
        }

        private static final Map<Integer, Action> map = new HashMap<>();
        static {
            for (Action action: values()) {
                map.put(action.ordinal(), action);
            }
        }
        public static Action valueOf(int ordinal) {
            return (Action) map.get(ordinal);
        }
    }

    @Override
    public boolean onCreate() {
        // Nothing to do here
        return true;
    }

    /**
     * Return the content URI for the required Action.
     * @param action the action to build the URI for.
     */
    public static Uri getUriForAction(Action action) {
        Uri.Builder builder = new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(action.path);
        if (action == Action.EXPORT_STACKTRACES) {
            int num = new Random().nextInt(9999999);
            builder.appendPath(String.format("%07d", num));
        }
        builder.appendPath(action.file_name); // To make it look nice in use menu's; otherwise not needed
        return builder.build();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selection_args, String sort_order) {
        File file = null;
        String file_name = null;

        List<String> segments = uri.getPathSegments();
        if (segments.size() == 2 && segments.get(0).equals(Action.EXPORT_DB.path)) {
            file = getDBFile();
            file_name = Action.EXPORT_DB.file_name;
        } else if (segments.size() == 3 && segments.get(0).equals(Action.EXPORT_STACKTRACES.path)) {
            file = getStacktraceFile(segments.get(1));
            file_name = Action.EXPORT_STACKTRACES.file_name;
        }
        if (file != null) {
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 1);
            cursor.addRow(new Object[]{file_name, file.length()});
            return cursor;
        }

        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        if (mode != "r") return null; // Only hand out readable files

        File file = null;
        List<String> segments = uri.getPathSegments();
        if (segments.size() == 2 && segments.get(0).equals(Action.EXPORT_DB.path)) {
            file = getDBFile();
        } else if (segments.size() == 3 && segments.get(0).equals(Action.EXPORT_STACKTRACES.path)) {
            file = getStacktraceFile(segments.get(1));
        }
        if (file != null) {
            try {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (FileNotFoundException e) { /* returning null; */}
        }

        return null;
    }

    @Override
    public String getType(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() == 2 && segments.get(0).equals(Action.EXPORT_DB.path)) {
            if (getDBFile() != null) {
                return "application/x-sqlite3";
            }
        } else if (segments.size() == 3 && segments.get(0).equals(Action.EXPORT_STACKTRACES.path)) {
            if (getStacktraceFile(segments.get(1)) != null) {
                return "text/plain";
            }
        }
        return null;
    }

    /**
     * Construct the File object to the internal SQLite database file.
     *
     * @return a File object pointing to the SQLite database, or null if it doesn't somehow exist.
     */
    private File getDBFile() {
        SQLiteDatabase db = DBHelper.getInstance(getContext()).getReadableDatabase();
        File file = new File(db.getPath());
        if (file.exists()) {
            return file;
        }
        return null;
    }

    /**
     * Construct a txt file listing the stacktraces for the last 60 days.
     *
     * @param id the 7-digit id for the request.
     * @return a File object to the txt file.
     */
    private File getStacktraceFile(String id) {
        // id should be a 7 digit string
        if (!id.matches("[0-9]{7}")) return null;

        File stacktrace_file = new File(getContext().getCacheDir(), "stacktraces.txt");

        if (!stacktrace_file.exists() || !id.equals(m_stacktraces_uri_id)) {
            // Generate or regenerate the file
            Thread.UncaughtExceptionHandler exception_handler = Thread.getDefaultUncaughtExceptionHandler();
            if (exception_handler instanceof ExceptionLogger) {
                m_stacktraces_uri_id = id;
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(stacktrace_file));
                    writer.write(((ExceptionLogger) exception_handler).getFormattedStackTraces());
                    writer.close();
                } catch (IOException e) {
                    Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                }
            } else {
                Exception e = new Exception("The default UncaughtExceptionHandler is not a ExceptionLogger, as was expected.");
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }

        if (stacktrace_file.exists() && id.equals(m_stacktraces_uri_id)) {
            return stacktrace_file;
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // Inserting is not supported
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selection_args) {
        // Deleting is not supported
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selection_args) {
        // Updating is not supported
        return 0;
    }
}
