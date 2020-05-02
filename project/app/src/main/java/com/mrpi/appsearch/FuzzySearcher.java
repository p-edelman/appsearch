package com.mrpi.appsearch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Base class to build fuzzy database searchers on.
 *
 * A fuzzy search means that names are matches if all  letters of the query are present,
 * in the presented order, but not necessarily adjacent to each other.
 *
 * The results are sorted in such a way that matches starting with the query are
 * presented first. The more characters between the query characters, the lower
 * on the list the result will be.
 *
 * @param <T> The class will eventually return a list of SearchData derived objects of this type.
 */
abstract public class FuzzySearcher<T extends FuzzySearchResult> {
    protected Context m_context;

    public FuzzySearcher(Context context) {
        m_context = context;
    }

    /**
     * Derived classes should implement this method to search the database for the given query.
     * Basically, this method should query the right db tables and fields and convert the results
     * to a T instance.
     *
     * @param db the database object to perform the search on
     * @param formatted_query a preformatted query to use in the SQL statement
     * @return an ArrayList containing the search results
     */
    abstract protected ArrayList<T> queryDB(SQLiteDatabase db, String formatted_query);

    /**
     * Search the database using queryDB() and sort the results by rating.
     *
     * @param query the query to search for
     * @return a list of results, sorted primarily by the amount of match between the query and
     *         the name and secondary by the order in the database.
     */
    public ArrayList<T> search(String query) {
        SQLiteDatabase db = DBHelper.getInstance(m_context).getReadableDatabase();
        String formatted_query = "%";
        for (int pos = 0; pos < query.length(); pos++) formatted_query += query.charAt(pos) + "%";
        ArrayList<T> results_list = queryDB(db, formatted_query);

        for (T result: results_list) {
            result.calcMatchRating(query);
        }

        // Sort by comparing the ratings. If ratings are equal, the order is preserved by sort().
        // This is needed, because the results are pre-sorted in the database. When two results
        // have an equal text match, the first one comes out on top.
        Collections.sort(results_list, new Comparator<T>() {
            public int compare(T result1, T result2) {
                return (result1.match_rating - result2.match_rating);
            }
        });

        return results_list;
    }
}
