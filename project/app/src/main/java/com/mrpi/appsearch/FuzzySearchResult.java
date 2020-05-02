package com.mrpi.appsearch;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * General container for holding a search result where a query is fuzy matched with a name (see
 * FuzzySearcher for a description of fuzzy matching).
 */
public abstract class FuzzySearchResult {
    /** The name of the result that was used for matching. */
    public String name;

    /** A measure of how much the query matches the name. */
    public int match_rating = 0;

    /** A list of the characters from the name that matches the query. */
    public ArrayList<Integer> char_matches = null;

    public FuzzySearchResult(String name) {
        this.name = name;
    }

    /**
     * Derived classes should implement this to render the icon for this search result.
     *
     * @param context the application context
     * @return a Drawable representing the icon for this search result
     */
    abstract Drawable resolveIcon(Context context);

    /**
     * Calculate the "amount of match" between query and string. The lower the
     * number, the better the query is contained in the name. Along the way, mark
     * the characters that match to the query.
     *
     * The lower the match rating, the better the match. -1 means the query is the
     * app name. If the query is contained is the name, the rating is the number
     * of characters in front of it. If the query letters are spread out over the
     * name, there's a penalty of 100 plus the number of characters in between.
     *
     * Search is done case insensitive.
     *
     * @param query a short string of characters to match against the name.
     * @return a SearchData object with its .match_rating and .char_matches
     *         parameters set.
     */
    public void calcMatchRating(String query) {
        char_matches = new ArrayList<Integer>();
        String norm_name = name.toLowerCase(Locale.US);

        int index = norm_name.indexOf(query);
        if (index != -1) {
            if ((index == 0) && (query.length() == norm_name.length())) {
                match_rating = -1; // Query is app name; we're golden!
            } else {
                match_rating = index; // Rating is the number of chars in front
                                      // of the query.
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
    }
}
