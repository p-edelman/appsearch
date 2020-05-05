package com.mrpi.appsearch;

import java.util.List;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Adapter to provide the data of the search results and present it in the
 * proper way.
 *
 * @param <T> the type of search result to hold, as a subclass of SearchResult
 */
public class SearchResultArrayAdapter<T extends SearchResult>
        extends ArrayAdapter<T> {

    // The list of search results we need to format.
    private List<T> m_search_result;

    // Flag to indicate whether the matched characters should be highlighted.
    private boolean m_render_clear;

    public SearchResultArrayAdapter(Context context, int textview_resource_id, List<T> search_result) {
        super(context, textview_resource_id, search_result);
        m_search_result   = search_result;
        m_render_clear    = false;
    }

    /**
     * Format a single app from the list and return it as a {@link View} that can
     * be attached to a GUI.
     *
     * @param position     in the app list.
     * @param convert_view a view to reuse..
     * @param parent       a parent to attach the returned view to.
     * @return a {@link View} that can be attached to a GUI.
     */
    @Override
    public View getView(int position, View convert_view, ViewGroup parent) {
        while (position < m_search_result.size()) {
            SearchResult search_result = m_search_result.get(position);
            View row_view = renderRow(search_result, convert_view, parent);
            if (row_view != null) {
                return row_view;
            } else if (search_result instanceof AppSearchResult) {
                // We're dealing with an app that is not installed anymore, so remove it from the
                // database and move to the next.
                // NOTE: it seems out of place to check for missing apps here, but it is in fact
                // quite efficient to handle missing apps here along the way than to perform an
                // explicit check each time for every search result.
                DBHelper db_helper = DBHelper.getInstance(getContext());
                db_helper.removePackage(((AppSearchResult) m_search_result).package_name);
                m_search_result.remove(position);
                notifyDataSetChanged();
            }
        }

        // Return empty row when one app was removed from the list
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.app_result, parent, false);
    }

    /**
     * Render a single row in the list.
     *
     * @param search_result The SearchResult object describing the result
     * @param parent        The parent view to attach the view to
     * @param convert_view  a possibly recycled view (see getView())
     * @return the rendered view, or None if rendering failed
     */
    private View renderRow(SearchResult search_result, View convert_view, ViewGroup parent) {
        // Instantiate or recycle the row view
        View row_view = null;
        if (convert_view != null) {
            row_view = convert_view;
        } else {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row_view = inflater.inflate(R.layout.app_result, parent, false);
        }

        // Set icon
        ImageView image_view = (ImageView) row_view.findViewById(R.id.AppIcon);
        Drawable icon = search_result.resolveIcon(getContext());
        if (icon == null) {
            return null;
        }
        image_view.setImageDrawable(icon);

        // Set text; make the matching letters underlined and bold
        TextView text_view = (TextView) row_view.findViewById(R.id.AppName);
        text_view.setText(search_result.name, TextView.BufferType.SPANNABLE);
        if (!m_render_clear && search_result.char_matches != null) {
            Spannable spannable = (Spannable) text_view.getText();
            for (Integer index : search_result.char_matches) {
                spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new UnderlineSpan(), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return row_view;
    }

    /**
     * Special method to re-render the results list but without the highlighting
     * of the matched characters.
     */
    public void renderClear() {
        m_render_clear = true;
        notifyDataSetChanged();
    }
}
