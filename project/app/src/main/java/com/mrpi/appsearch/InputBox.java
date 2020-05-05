package com.mrpi.appsearch;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.widget.EditText;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * EditText widget with custom rendering, which is meant to be used as the main input/search box
 * for the app.
 *
 * Instead of rendering the typed text like a normal input box, it shows the best match for the
 * input and only highlights the letters that match.
 *
 * This is a "dumb" class that only provides rendering, not matching of input.
 */
public class InputBox extends EditText {

    /** The search result that currently best matches to the input. */
    private SearchResult m_matching_result;

    /** Whether to render the matching app without highlighting the matched letters. */
    private boolean m_render_clear = false;

    /** The text view holding the rendered text with matching characters highlighted. This is cached
     *  because onDraw() is called again by Android on each cursor blink */
    private TextView m_text_view;

    /**
     * Variables needed to paint a blinking cursor.
     */
    Paint.FontMetrics m_font_metrics;   // Font metrics for this EditText
    private int m_cursor_pos;           // The position in pixels of the cursor. -1 is used to
                                        // indicate that is should be calculated.
    private boolean m_cursor_on = true; // Whether the cursor currently should be shown or not.

    // The Drawable used as text cursor. Note: this should normally be set using the
    // "textCursorDrawabale" EditText property, but there's no way to obtain it prior to API
    // level 29.
    private Drawable m_cursor_drawable = getResources().getDrawable(R.drawable.text_cursor);

    /** Allow for some margin before we start drawing text on the canvas. */
    private int m_text_padding_left = 5 * (int) getResources().getDisplayMetrics().density;

    public InputBox(Context context, AttributeSet attrs) {
        super(context, attrs);

        TextPaint text_paint = getPaint();
        m_font_metrics = text_paint.getFontMetrics();
    }

    /**
     * Set the best matching search result for the input text.
     *
     * @param search_result the SearchData object for the best match.
     */
    public void setMatchingSearchResult(SearchResult search_result) {
        m_matching_result = search_result;

        // Render the new text view and clear the cursor position. Note: we can't calculate the
        // cursor position yet because we don't have a canvas to draw on.
        m_text_view = renderText(false);
        m_cursor_pos = -1;
        m_cursor_on = true;

        invalidate();
    }

    /**
     * Whether to render the matching app without highlighting the matched letters.
     *
     * @param render_clear if true, render without highlighting.
     */
    public void renderClear(boolean render_clear) {
        if (render_clear != m_render_clear) { // Only redraw if there's a change
            m_render_clear = render_clear;

            // Redraw the text, using the m_render_clear flag
            m_text_view = renderText(false);

            if (render_clear) {
                // If we're rendering clear, we know the cursor position should be 0
                m_cursor_pos = 0;
            } else {
                // Otherwise, it needs to be calculated
                m_cursor_pos = -1;
            }
            m_cursor_on = true;

            invalidate();
        }
    }

    /**
     * Perform the rendering of the app name and highlight the matching letters, or render a
     * "no match" message if there is no matching app.
     * The suggested part of the text is rendered using the textColorHint, while the matching
     * letters are rendered using the textColor. The warning message is rendered using
     * textColorHighlight.
     *
     * @param is_capped if true, only render the app name up to the last matching letter.
     * @return a TextView with the rendered app name.
     */
    private TextView renderText(boolean is_capped) {
        TextView text_view = new TextView(getContext());

        // Use the rendering parameters declared for this EditText.
        text_view.setTypeface(getTypeface());
        text_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize());

        if (m_matching_result != null) {
            text_view.setTextColor(getCurrentHintTextColor());

            // Set the text
            ArrayList<Integer> matches = m_matching_result.char_matches;
            if (is_capped) {
                if (matches != null && m_render_clear == false) {
                    int to_pos = matches.get(matches.size() - 1) + 1;
                    text_view.setText(m_matching_result.name.substring(0, to_pos), BufferType.SPANNABLE);
                }
            } else {
                text_view.setText(m_matching_result.name, BufferType.SPANNABLE);
            }

            // Highlight matched letters
            if (m_matching_result.char_matches != null && m_render_clear == false) {
                Spannable spannable = (Spannable) text_view.getText();
                for (Integer i : matches) {
                    spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new UnderlineSpan(), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new ForegroundColorSpan(getCurrentTextColor()), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } else if (getText().length() > 0) {
            // Render a special string to indicate that there's no match (but only if there's actual
            // text typed.

            text_view.setTextColor(getCurrentTextColor());

            if (is_capped) {
                text_view.setText(getText());
            } else {
                text_view.setText(getText());
                text_view.setText(String.format("%s: %s", getText(), getContext().getString(R.string.no_match)), BufferType.SPANNABLE);
                Spannable spannable = (Spannable) text_view.getText();
                spannable.setSpan(new ForegroundColorSpan(getCurrentHintTextColor()), getText().length(), getText().length() + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new StyleSpan(Typeface.ITALIC), getText().length() + 2, text_view.getText().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(getHighlightColor()), getText().length() + 2, text_view.getText().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return text_view;
    }

    /**
     * Render our text box. This method is called by Android again on each cursor blink.
     */
    @Override
    public void onDraw(Canvas canvas) {
        // The actual width of the canvas that we can use
        int canvas_width = canvas.getWidth() - m_text_padding_left;

        // If we don't have a cursor position, calculate it by rendering a capped version of the
        // text and measuring its width..
        if (m_cursor_pos == -1) {
            TextView cursor_text = renderText(true);
            cursor_text.measure(canvas.getWidth(), canvas.getHeight());
            m_cursor_pos = cursor_text.getMeasuredWidth();
        }

        if (m_text_view.getWidth() == 0) {
            m_text_view.measure(canvas.getWidth(), canvas.getHeight());

            // If the cursor falls outside of the viewport, we will shift the canvas left in the
            // next step, so that it's still visible, but we have to use a larger size to accomodate
            // the extra space.
            int width = (m_cursor_pos + m_text_padding_left > canvas_width) ? m_cursor_pos + m_text_padding_left : canvas_width;
            m_text_view.layout(0, 0, width, canvas.getHeight());
        }

        // Position the canvas; add some space to the left, unless the cursor falls outside the
        // viewport, in which case we will shift the text as far left as needed to make the cursor
        // visible again.
        if (m_cursor_pos > canvas_width) {
            // Note: using canvas_width instead of canvas.getWidth() actually shifts the canvas too
            // much left, by an amount of m_text_padding_left. It works out because we need a
            // little bit of extra space to the right so the cursor doesn't touch the edge.
            canvas.translate(canvas_width - m_cursor_pos, 0);
        } else {
            canvas.translate(m_text_padding_left, 0);
        }

        // Render the cursor
        if (m_cursor_on) {
            int left = m_cursor_pos;
            m_cursor_drawable.setBounds(left, (int) m_font_metrics.descent, left + m_cursor_drawable.getIntrinsicWidth(), (int) (m_font_metrics.descent - m_font_metrics.ascent));
            m_cursor_drawable.draw(canvas);
            m_cursor_on = false;
        } else {
            m_cursor_on = true;
        }

        m_text_view.draw(canvas);
    }
}
