package com.mrpi.appsearch;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/**
 * Base class to build fuzzy database searchers on.
 *
 * A fuzzy search means that names are matches if all letters of the query are present,
 * in the same order, but not necessarily adjacent to each other.
 *
 * The match rating will be lowest (best) for matches starting with the query. -1 means the query
 * is the name itself. The more characters there are between the query characters, the higher the
 * rating will be. If the query is contained in the name but doesn't start with it, there's an
 * extra penalty of 100.
 *
 * @param <T> The class will eventually return a list of SearchData derived objects of this type.
 */
abstract public class FuzzySearcher<T extends SearchResult> {
    protected Context m_context;

    public FuzzySearcher(Context context) {
        m_context = context;
    }

    /**
     * Search the database using queryDB() and sort the results by rating.
     *
     * @param query the query to search for
     * @return a list of results, sorted primarily by the amount of match between the query and
     *         the name and secondary by the order in the database.
     */
    public ArrayList<T> search(String query) {
        SQLiteDatabase db = DBHelper.getInstance(m_context).getReadableDatabase();
        ArrayList<T> results_list = queryDB(db, query);

        // Set the matching characteristics to a fuzzy match
        for (T result: results_list) {
            mapToQuery(result, query);
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

    /**
     * Derived classes should implement this method to search the database for the given query.
     * Basically, this method should query the right db tables and fields and convert the results
     * to a T instance.
     *
     * @param db the database object to perform the search on
     * @param query the query to search on. Use formatQueryForDB() to get a version to use in the
     *              SQL statement
     * @return an ArrayList containing the search results
     */
    abstract protected ArrayList<T> queryDB(SQLiteDatabase db, String query);

    /**
     * Build a query that can be used on the database for fuzzy SQL statements.
     *
     * @param query the normal query
     * @return the same query with percent characters between each letter, which can be used for
     *         fuzzy searching with SQL.
     */
    protected String formatQueryForDB(String query) {
        String formatted_query = "%";
        for (int pos = 0; pos < query.length(); pos++) formatted_query += query.charAt(pos) + "%";
        return formatted_query;
    }

    /**
     * Set the match rating and matching characters of a match according to the fuzzy search rules.
     *
     * The match is done case insensitive.
     *
     * @param search_result the search result of which the match parameters need to be set
     * @param query a short string of characters to match against the name.
     */
    private void mapToQuery(T search_result, String query) {
        ArrayList<Integer> char_matches = new ArrayList<>();
        int match_rating = 1000;
        String norm_name = search_result.name.toLowerCase(Locale.US);

        int index = norm_name.indexOf(query);
        if (index != -1) {
            if ((index == 0) && (query.length() == norm_name.length())) {
                match_rating = -1; // Query is app name; we're golden!
            } else {
                match_rating = index; // Rating is the number of chars in front of the query.
            }
            // Mark the matching characters
            for (int i = index; i < (index + query.length()); i++) {
                char_matches.add(i);
            }
        } else {
            int rating = 100; // Query is not contained as whole in app name, which
            // means results should sink to the bottom. Therefore
            // the rating gets a penalty of 100.
            int name_pos = -1;
            char query_char, name_char;
            for (int query_pos = 0; query_pos < query.length(); query_pos++) {
                name_pos += 1;
                query_char = query.charAt(query_pos);
                name_char = norm_name.charAt(name_pos);
                while (name_char != query_char) {
                    rating += 1;
                    name_pos += 1;
                    name_char = norm_name.charAt(name_pos);
                }
                char_matches.add(name_pos);
            }
            match_rating = rating;
        }

        search_result.match_rating = match_rating;
        search_result.char_matches = char_matches;
    }
}
