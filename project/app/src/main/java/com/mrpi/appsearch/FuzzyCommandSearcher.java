package com.mrpi.appsearch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;

/**
 * Fuzzy searcher (see base class for an explanation) for commands - known text strings that the
 * user can type for advanced/debugging options (see CommandSearchData for more info).
 */
public class FuzzyCommandSearcher extends FuzzySearcher<CommandSearchResult> {
    public FuzzyCommandSearcher(Context context) {
        super(context);
    }

    protected ArrayList<CommandSearchResult> queryDB(SQLiteDatabase db, String query) {
        Cursor cursor = db.rawQuery("SELECT DISTINCT name, command_code FROM " + DBHelper.TBL_COMMANDS + " WHERE name LIKE ?",
                new String[]{formatQueryForDB(query)});

        final ArrayList<CommandSearchResult> commands = new ArrayList<>();
        boolean result = cursor.moveToFirst();
        while (result) {
            CommandSearchResult command_data = new CommandSearchResult(cursor.getString(0),
                    CommandSearchResult.CommandCode.values()[cursor.getInt(1)]);
            commands.add(command_data);
            result = cursor.moveToNext();
        }

        return commands;
    }
}
