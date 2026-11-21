package au.rifthouse.kiosk;

import java.time.LocalDate;

/** Works out which seasonal theme is on for a given day. */
final class Season {
    static final int NONE = 0, CHRISTMAS = 1, NEWYEAR = 2, EASTER = 3, HALLOWEEN = 4;

    /** What the Theme choice in Settings offers, in order. */
    static final String[] KEYS = {"auto", "off", "christmas", "newyear", "easter", "halloween"};
    static final String[] LABELS = {"Automatic (by the date)", "No theme", "Christmas", "New Year", "Easter", "Halloween"};

    private Season() { }

    /** The theme to show: a fixed choice from Settings, or the one that suits the date when it is on Automatic. */
    static int resolve(String pref, LocalDate today) {
        switch (pref == null ? "auto" : pref) {
            case "off": return NONE;
            case "christmas": return CHRISTMAS;
            case "newyear": return NEWYEAR;
            case "easter": return EASTER;
            case "halloween": return HALLOWEEN;
            default: return forDate(today);
        }
    }

    /**
     * Automatic dates:
     * Halloween 24 Oct to 1 Nov, Christmas 1 Dec to 26 Dec, New Year 27 Dec to 3 Jan,
     * Easter from two weeks before Easter Sunday to Easter Monday.
     */
    static int forDate(LocalDate d) {
        LocalDate easter = easterSunday(d.getYear());
        if (!d.isBefore(easter.minusDays(14)) && !d.isAfter(easter.plusDays(1))) return EASTER;
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if ((m == 10 && day >= 24) || (m == 11 && day == 1)) return HALLOWEEN;
        if (m == 12 && day <= 26) return CHRISTMAS;
        if ((m == 12 && day >= 27) || (m == 1 && day <= 3)) return NEWYEAR;
        return NONE;
    }

    /** Easter Sunday (the usual Gregorian calculation). */
    static LocalDate easterSunday(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, month, day);
    }
}
