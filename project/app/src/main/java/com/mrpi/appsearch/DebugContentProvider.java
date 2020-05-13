package com.mrpi.appsearch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * A ContentProvider that shares debug information about the app, which is the only thing this
 * app needs to share.
 *
 * As a matter of fact, this ContentProvider only accepts one, fixed uri, and will return the
 * current internal database for it. All arguments etc. have no meaning.
 */
public class DebugContentProvider extends ContentProvider {

    /** The "authority" for content URI's for this app. */
    private static final String AUTHORITY = "org.mrpi.appsearch.provider";

    /** The path to signal the "export database" action */
    private static final String EXPORT_DB = "export_db";

    @Override
    public boolean onCreate() {
        // Nothing to do here
        return true;
    }

    /**
     * Return the content uri for signalling the export of the database.
     */
    public static Uri getUriForDBExport() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(EXPORT_DB).build();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selection_args, String sort_order) {
        File file = getFileForUri(uri);
        if (file != null) {
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 1);
            cursor.addRow(new Object[]{"AppSearch.sqlite", file.length()});
            return cursor;
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        if (mode != "r") return null; // Only hand out readable files

        File file = getFileForUri(uri);
        try {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException e) { /* returning null; */}
        return null;
    }

    @Override
    public String getType(Uri uri) {
        if (getFileForUri(uri) != null) {
            return "application/x-sqlite3";
        }

        return null;
    }

    /**
     * Construct the File object that should be returned for the supplied uri path, where there's
     * just one option: a path of "export_db" results in a File object pointing to the SQLite
     * database on disk.
     *
     * @param uri the uri of the request
     * @return a File object pointing to the SQLite database if the uri path is "export_db", or
     *         null if it doesn't match.
     */
    private File getFileForUri(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() == 1 && segments.get(0).equals(EXPORT_DB)) {
            SQLiteDatabase db = DBHelper.getInstance(getContext()).getReadableDatabase();
            File file = new File(db.getPath());
            if (file.exists()) {
                return file;
            }
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
