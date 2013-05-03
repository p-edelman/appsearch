package com.mrpi.appsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AboutDialog extends DialogFragment {
  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup parent,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.dialog_about, parent);
    TextView text_view = (TextView)view.findViewById(R.id.AboutDialog);
    InputStreamReader in_stream = new InputStreamReader(getResources().openRawResource(R.raw.about));
    BufferedReader buf = new BufferedReader(in_stream);
    String line;
    StringBuilder html_text = new StringBuilder();
    try {
      while ((line = buf.readLine()) != null) html_text.append(line);
    } catch (IOException e) {
      // Too bad
    }

    text_view.setText(Html.fromHtml(html_text.toString()));
    
    return view;
  }
  
}
