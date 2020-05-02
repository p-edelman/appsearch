package com.mrpi.appsearch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

/**
 * Container for holding the data of an app.
 */
public class FuzzyAppSearchResult extends FuzzySearchResult {
    public String package_name;

    /**
     * Initialize the app info container.
     *
     * @param name         the public display name of the app
     * @param package_name the full package name of the app
     */
    public FuzzyAppSearchResult(String name, String package_name) {
        super(name);
        this.package_name = package_name;
    }

    /**
     * Try to find the app icon. Return null if it cannot be found, which is an indication that the
     * app is not available anymore.

     * @param context the application context
     * @return the icon Drawable or null
     */
    public Drawable resolveIcon(Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(package_name);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other != null) {
            if (getClass() == other.getClass()) {
                if (package_name.equals(((FuzzyAppSearchResult) other).package_name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
