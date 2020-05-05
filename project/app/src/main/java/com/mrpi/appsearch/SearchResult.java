package com.mrpi.appsearch;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

/**
 * General container for holding a search result, mainly targeted towards displaying it in the app.
 *
 * A search result holds a name that is used for searching, a ranking representing how well the
 * result matches the query, and a list of the letters from the name that match the search query.
 */
public abstract class SearchResult {
    /** The name of the result that was used for matching. */
    public String name;

    /**
     * A measure of how much the query matches the name. The lower the match rating, the better the
     * match.
     */
    public int match_rating = 0;

    /** The characters from the name that match the query. */
    public ArrayList<Integer> char_matches = null;

    /**
     * Simple constructor.
     *
     * @param name the public display name, where the match was made on.
     */
    public SearchResult(String name) {
        this.name = name;
    }

    /**
     * Derived classes should implement this to render the icon for this search result.
     *
     * @param context the application context
     * @return a Drawable representing the icon for this search result
     */
    abstract Drawable resolveIcon(Context context);
}
