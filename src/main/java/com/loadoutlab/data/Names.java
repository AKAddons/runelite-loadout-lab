package com.loadoutlab.data;

import java.util.Locale;

/** Name matching is lower-case, locale-free, everywhere. */
public class Names {

    public static String lower(String s) { return s.toLowerCase(Locale.ROOT); }

    /** The first letter upper-cased, the rest as it was; null and "" read "". */
    public static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
