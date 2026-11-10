package com.unihub.app.util;

/** Student roll format: 2307 followed by three digits, from 001 to 123 (2307001 - 2307123). */
public class RollValidator {
    public static boolean isValid(String roll) {
        if (roll == null || !roll.matches("2307\\d{3}")) return false;
        int last = Integer.parseInt(roll.substring(4));
        return last >= 1 && last <= 123;
    }
}
