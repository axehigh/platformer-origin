package com.axehigh.platformer.util;

/**
 * GWT-safe number formatting. The GWT compiler (web/HTML target) does not support
 * {@link String#format}, so HUD/inventory counters build their text with these helpers instead.
 */
public final class NumFormat {

    private NumFormat() {
    }

    /**
     * Zero-pads {@code value} to at least {@code digits} characters, e.g. {@code pad(7, 4)} is
     * {@code "0007"}. Values already at or over the requested width are returned unchanged.
     */
    public static String pad(int value, int digits) {
        String text = Integer.toString(value);
        int missing = digits - text.length();
        if (missing <= 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < missing; i++) {
            sb.append('0');
        }
        return sb.append(text).toString();
    }

    /**
     * Substitutes each {@code %d} in {@code template}, left to right, with the given values.
     * A {@code %d} with no matching argument is left as-is.
     */
    public static String format(String template, int... values) {
        StringBuilder sb = new StringBuilder(template.length() + 8);
        int nextArg = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '%' && i + 1 < template.length() && template.charAt(i + 1) == 'd'
                && nextArg < values.length) {
                sb.append(values[nextArg++]);
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
