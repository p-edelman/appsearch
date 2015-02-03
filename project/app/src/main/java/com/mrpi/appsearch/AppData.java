package com.mrpi.appsearch;

import java.util.ArrayList;

/** Container for holding the data of an app. Nothing fancy here.
 *  Of all the data, only name and package_name need to be set. Anything using this class should
 *  expect that the other fields may not be set. */
public class AppData {
  public String name;
  public String package_name;
  public int match_rating = 0;
  public ArrayList<Integer> char_matches = null;

  public AppData(String name, String package_name) {
    this.name = name;
    this.package_name = package_name;
  }

  @Override
  public boolean equals(Object other) {
    if (other != null) {
      if (getClass() == other.getClass()) {
        if (package_name.equals(((AppData) other).package_name)) {
          return true;
        }
      }
    }
    return false;
  }
}
