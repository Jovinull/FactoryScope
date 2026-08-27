package factoryscope.ui;

import java.util.*;

/**
 * Number formatting for the panel.
 *
 * <p>Rates that come out of the game are floats accumulated over frames, so a smelter that produces
 * three quarters of an item per second reports 0.74999994. Printing that verbatim would make the mod
 * look broken, so precision is chosen from the magnitude of the value and trailing zeros are dropped.
 *
 * <p>Deliberately free of Arc and Mindustry types so it can be tested directly.
 */
public final class Numbers{
    private Numbers(){
    }

    /** A production or consumption rate, without the unit suffix. */
    public static String rate(float value){
        if(!finite(value)) return "?";
        float magnitude = Math.abs(value);
        if(magnitude >= 1000f) return String.format(Locale.ROOT, "%.0f", value);
        if(magnitude >= 100f) return trim(String.format(Locale.ROOT, "%.1f", value));
        if(magnitude >= 10f) return trim(String.format(Locale.ROOT, "%.2f", value));
        //below 10 a third digit is where craft rates such as 0.375/s actually live
        return trim(String.format(Locale.ROOT, "%.3f", value));
    }

    /** An amount held in a building; whole numbers stay whole. */
    public static String amount(float value){
        if(!finite(value)) return "?";
        if(Math.abs(value - Math.round(value)) < 0.005f) return Integer.toString(Math.round(value));
        return trim(String.format(Locale.ROOT, "%.2f", value));
    }

    /** A [0, 1] fraction as a percentage. Values that are close to but not exactly full stay honest. */
    public static String percent(float fraction){
        if(!finite(fraction)) return "?";
        float percent = fraction * 100f;
        if(percent > 0f && percent < 1f) return "<1%";
        if(percent < 100f && percent > 99f) return ">99%";
        return Math.round(percent) + "%";
    }

    /** A multiplier such as an overdrive or terrain boost, e.g. {@code 1.5x}. */
    public static String multiplier(float value){
        if(!finite(value)) return "?";
        return trim(String.format(Locale.ROOT, "%.2f", value)) + "x";
    }

    /** A signed value, used for power balance where the sign carries the meaning. */
    public static String signedRate(float value){
        if(!finite(value)) return "?";
        String text = rate(value);
        return value > 0f && !text.startsWith("+") ? "+" + text : text;
    }

    private static String trim(String text){
        if(text.indexOf('.') < 0) return text;
        int end = text.length();
        while(end > 0 && text.charAt(end - 1) == '0') end--;
        if(end > 0 && text.charAt(end - 1) == '.') end--;
        String trimmed = text.substring(0, end);
        return trimmed.isEmpty() || trimmed.equals("-") ? "0" : trimmed;
    }

    private static boolean finite(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
