package com.mrpi.appsearch;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.EnumMap;

/**
 * Container for holding the data of a "command", a text string that the user can type into the
 * search box to access developer options.
 */
public class FuzzyCommandSearchResult extends FuzzySearchResult {
    /**
     * Enum for listing all the commands that we know of.
     */
    public enum CommandCode {
        EXPORT_DB,
        COLLECT_RAW,
        DONT_COLLECT_RAW
    }

    public CommandCode command;

    public FuzzyCommandSearchResult(String name, CommandCode command) {
        super(name);
        this.command = command;
    }

    /**
     * Helper method to add all known commands to a database with their full command string and
     * their Enum number. This method should be called by DBHelper when installing/upgrading the
     * database.
     *
     * @param db a SQLiteDatabase object to write to
     * @param table the name of the table where the commands will be stored.
     */
    public static void initializeDB(SQLiteDatabase db, String table) {
        EnumMap<CommandCode, String> commands = new EnumMap<CommandCode, String>(CommandCode.class);
        commands.put(CommandCode.EXPORT_DB, "/export database");
        commands.put(CommandCode.COLLECT_RAW, "/collect raw data");
        commands.put(CommandCode.DONT_COLLECT_RAW, "/don't collect raw data");

        for (CommandCode code : commands.keySet()) {
            ContentValues values = new ContentValues();
            values.put("name", commands.get(code));
            values.put("command_code", code.ordinal());
            db.insert(table, null, values);
        }

    }

}
