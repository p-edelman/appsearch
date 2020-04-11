package com.mrpi.appsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AboutDialog extends DialogFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup parent,
                             Bundle savedInstanceState) {
        // Get hold of the UI view
        View view = inflater.inflate(R.layout.dialog_about, parent);
        TextView text_view = (TextView) view.findViewById(R.id.AboutDialog);

        // Read the HTML file
        InputStreamReader in_stream = new InputStreamReader(getResources().openRawResource(R.raw.about));
        BufferedReader buf = new BufferedReader(in_stream);
        StringBuilder html_text = new StringBuilder();
        String line;
        try {
            while ((line = buf.readLine()) != null) html_text.append(line);
        } catch (IOException e) {
            // Too bad
        }

        // Set the HTML to the view
        text_view.setText(Html.fromHtml(html_text.toString()));
        text_view.setMovementMethod(LinkMovementMethod.getInstance()); // Needed for clickable URLs

        return view;
    }
}
