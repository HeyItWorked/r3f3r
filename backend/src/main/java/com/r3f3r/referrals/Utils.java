package com.r3f3r.referrals;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// random helper stuff
public class Utils {

    public static String STATUS_DONE = "DONE";
    public static int MAX_LENGHT = 100;

    // check if string is empty
    public static boolean isEmpty(String s) {
        if (s == null) {
            return true;
        } else {
            if (s.trim().length() == 0) {
                return true;
            } else {
                return false;
            }
        }
    }

    public static int lenght(String s) {
        if (s == null) return 0;
        return s.trim().length();
    }

    // returns null if its not a date
    public static LocalDate parseDate(String d) {
        try {
            return LocalDate.parse(d);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // makes "NEW, SENT, or DONE"
    public static String joinStatuses(List<String> stuff) {
        String result = "";
        for (int i = 0; i < stuff.size(); i++) {
            if (i > 0) result += (i == stuff.size() - 1) ? ", or " : ", ";
            result += stuff.get(i);
        }
        return result;
    }

    // map("a", 1, "b", 2) -> {a=1, b=2}
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    public static Map<String, String> oneField(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    // old, not used anymore
    public static boolean isValidStatus2(String s) {
        return s != null && (s.equals("NEW") || s.equals("SENT") || s.equals("DONE") || s.equals("CANCELLED"));
    }
}
