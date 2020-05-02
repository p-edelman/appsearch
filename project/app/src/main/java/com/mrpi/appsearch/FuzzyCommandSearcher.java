package com.mrpi.appsearch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

/**
 * Fuzzy searcher (see the base class FuzzySearch for an explanation of "fuzzy search") for
 * commands - known text strings that the user can type for advanced/debugging options
 * (see CommandSearchData for more info).
 */
public class FuzzyCommandSearcher extends FuzzySearcher<FuzzyCommandSearchResult> {
    public FuzzyCommandSearcher(Context context) {
        super(context);
    }

    protected ArrayList<FuzzyCommandSearchResult> queryDB(SQLiteDatabase db, String formatted_query) {
        Cursor cursor = db.rawQuery("SELECT DISTINCT name, command_code FROM " + DBHelper.TBL_COMMANDS + " WHERE name LIKE ?", new String[]{formatted_query});

        // Put the results in a list of AppData.
        final ArrayList<FuzzyCommandSearchResult> command_list = new ArrayList<>();
        boolean result = cursor.moveToFirst();
        while (result) {
            FuzzyCommandSearchResult command_data = new FuzzyCommandSearchResult(cursor.getString(0), FuzzyCommandSearchResult.CommandCode.values()[cursor.getInt(1)]);
            command_list.add(command_data);
            result = cursor.moveToNext();
        }

        return command_list;
    }
}
