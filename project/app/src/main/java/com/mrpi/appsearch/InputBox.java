package com.mrpi.appsearch;

import android.graphics.Color;
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

/** EditText widget with custom rendering, which is meant to be used as the main input/search box
 *  for the app.
 *
 *  Instead of rendering the typed text like a normal input box, it shows the best match for the
 *  input and only highlights the letters that match.
 *
 *  This is a "dumb" class that only provides rendering, not matching of input.
 */
public class InputBox extends EditText {

    /** The app that currently gives the best match to the input. */
    private AppData m_matching_app;

    /** Whether to render the matching app without highlighting the matched letters. */
    private boolean m_render_clear = false;

    /** The text view holding the rendered text with matching characters highlighted. This is cached
     *  because onDraw() is called again by Android on each cursor blink. */
    private TextView m_text_view;

    /** Variables needed to paint a blinking cursor. */
    Paint.FontMetrics m_font_metrics;     // Font metrics for this EditText
    private int       m_cursor_pos;       // The position in pixels of the cursor. -1 is used to
                                          // indicate that is should be calculated.
    private boolean   m_cursor_on = true; // Whether the cursor currently should be shown or not.

    // The Drawable used as text cursor. Note: this should normally be set using the
    // "textCursorDrawabale" EditText property, but there's no way to obtain it prior to API
    // level 29.
    private Drawable  m_cursor_drawable = getResources().getDrawable(R.drawable.text_cursor);

    /** Allow for some margin before we start drawing text on the canvas. */
    private int m_text_padding_left = 5 * (int)getResources().getDisplayMetrics().density;

    public InputBox(Context context, AttributeSet attrs) {
        super(context, attrs);

        TextPaint text_paint = getPaint();
        m_font_metrics = text_paint.getFontMetrics();
    }

    /** Set the best matching app for the input text.
     *  @param app_data the AppData object for the best match. */
    public void setMatchingApp(AppData app_data) {
        m_matching_app = app_data;

        // Render the new text view and clear the cursor position. Note: we can't calculate the
        // cursor position yet because we don't have a canvas to draw on.
        m_text_view = renderText(false);
        m_cursor_pos = -1;
        m_cursor_on = true;

        invalidate();
    }

    /** Whether to render the matching app without highlighting the matched letters.
     *  @param render_clear if true, render without highlighting. */
    public void renderClear(boolean render_clear) {
        m_render_clear = render_clear;
        m_text_view = renderText(false);
        m_cursor_pos = 0;

        invalidate();
    }

    /** Perform the rendering of the app name and highlight the matching letters.
     *  @param is_capped if true, only render the app name up to the last matching letter.
     *  @return a TextView with the rendered app name.
     */
    private TextView renderText(boolean is_capped) {
        TextView text_view = new TextView(getContext());

        if (m_matching_app == null) {
            return text_view;
        }

        // Use the rendering parameters declared for this EditText.
        text_view.setTypeface(getTypeface());
        text_view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize());
        text_view.setTextColor(getCurrentTextColor());

        // Set the text
        if (is_capped) {
            if (m_matching_app.char_matches != null && m_render_clear == false) {
                int to_pos = m_matching_app.char_matches.get(m_matching_app.char_matches.size() - 1) + 1;
                text_view.setText(m_matching_app.name.substring(0, to_pos), BufferType.SPANNABLE);
            }
        } else {
            text_view.setText(m_matching_app.name, BufferType.SPANNABLE);
        }

        // Highlight matched letters
        if (m_matching_app.char_matches != null && m_render_clear == false) {
            Spannable spannable = (Spannable)text_view.getText();
            for (Integer i : m_matching_app.char_matches) {
                spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new UnderlineSpan(), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(Color.BLACK), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return text_view;
    }

    /** Render our text box. This method is called by Android again on each cursor blink. */
    @Override
    public void onDraw(Canvas canvas) {
        if (m_matching_app != null) {
            if (m_text_view.getWidth() == 0) {
                m_text_view.measure(canvas.getWidth(), canvas.getHeight());
                m_text_view.layout(0, 0, canvas.getWidth(), canvas.getHeight());
            }

            // Render the cursor
            if (m_cursor_pos == -1) {
                // Calculate the cursor position by rendering a capped version of the text and
                // measuring its width.
                TextView cursor_text = renderText(true);
                cursor_text.measure(canvas.getWidth(), canvas.getHeight());
                m_cursor_pos = cursor_text.getMeasuredWidth();
            }
            if (m_cursor_on) {
                int left = m_text_padding_left + m_cursor_pos;
                m_cursor_drawable.setBounds(left, (int)m_font_metrics.descent, left + m_cursor_drawable.getIntrinsicWidth(), (int)(m_font_metrics.descent - m_font_metrics.ascent));
                m_cursor_drawable.draw(canvas);
                m_cursor_on = false;
            } else {
                m_cursor_on = true;
            }

            // Add some space to the left of the text
            canvas.translate(m_text_padding_left, 0);

            m_text_view.draw(canvas);
        }
    }
}
