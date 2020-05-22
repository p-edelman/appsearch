package com.mrpi.appsearch;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;

import java.util.EnumMap;

/**
 * Container for holding the data of a "command", a text string that the user can type into the
 * search box to access developer options.
 */
public class CommandSearchResult extends SearchResult {
    /**
     * Enum for listing all the commands that we know of.
     */
    public enum CommandCode {
        EXPORT_DB,
        COLLECT_RAW_CLICKS,
        DONT_COLLECT_RAW_CLICKS,
        EXPORT_STACKTRACES,
    }

    public CommandCode command;

    public CommandSearchResult(String name, CommandCode command) {
        super(name);
        this.command = command;
    }

    /**
     * A command icon is always the built-in "settings" icon (ic_menu_manage).
     *
     * @param context the application context
     * @return a Drawable from ic_menu_manage.
     */
    public Drawable resolveIcon(Context context) {
        return context.getResources().getDrawable(android.R.drawable.ic_menu_manage);
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
        commands.put(CommandCode.EXPORT_STACKTRACES, "/export stacktraces");
        commands.put(CommandCode.COLLECT_RAW_CLICKS, "/log app openings");
        commands.put(CommandCode.DONT_COLLECT_RAW_CLICKS, "/don't log app openings");

        for (CommandCode code : commands.keySet()) {
            ContentValues values = new ContentValues();
            values.put("name", commands.get(code));
            values.put("command_code", code.ordinal());
            db.replace(table, null, values);
        }

    }

}
