package factoryscope.model;

import java.util.Objects;

/**
 * One family-specific detail about a building, such as the ore under a drill or a turret's ammunition.
 *
 * <p>The probe records values, never sentences: {@link #labelKey} names a bundle entry and the panel
 * formats {@link #value} according to {@link #format}. {@link #estimate} marks a figure that depends on
 * conditions the probe cannot observe, such as a separator's random output or a turret that keeps firing;
 * the panel must present such a figure as an estimate.
 */
public final class Fact{
    public enum Format{
        /** {@link #text} as given: a content name the game already localized. */
        text,
        /** {@link #value} as a [0, 1] fraction. */
        percent,
        /** {@link #value} of {@link #total}. */
        amountOf,
        /** {@link #value} seconds. */
        seconds,
        /** {@link #value} as a speed multiplier. */
        multiplier,
        /** {@link #value} as a whole count. */
        count,
        /** {@link #value} items per second. */
        itemsPerSecond,
        /** {@link #value} liquid units per second. */
        liquidPerSecond,
        /** {@link #value} power units per second. */
        powerPerSecond,
        /** {@link #value} occurrences per second, such as shots. */
        perSecond,
        /** {@link #value} occurrences per minute, such as units built. */
        perMinute,
        /** {@link #value} heat units. */
        heat,
        /** Yes when {@link #value} is positive, no otherwise. */
        flag
    }

    /** How the value should read: neutral, good news, something to watch, or something going wrong. */
    public enum Tone{
        normal,
        good,
        warning,
        danger
    }

    public final String labelKey;
    public final Format format;
    public final float value;
    public final float total;
    public final String text;
    /** Optional icon: the kind and content id of the resource the fact is about. */
    public final ResourceKind iconKind;
    public final String iconId;
    public final Tone tone;
    public final boolean estimate;

    private Fact(String labelKey, Format format, float value, float total, String text,
                 ResourceKind iconKind, String iconId, Tone tone, boolean estimate){
        this.labelKey = Objects.requireNonNull(labelKey, "labelKey");
        this.format = Objects.requireNonNull(format, "format");
        this.value = value;
        this.total = total;
        this.text = text;
        this.iconKind = iconKind;
        this.iconId = iconId;
        this.tone = Objects.requireNonNull(tone, "tone");
        this.estimate = estimate;
    }

    public static Fact of(String labelKey, Format format, float value){
        return new Fact(labelKey, format, value, -1f, null, null, null, Tone.normal, false);
    }

    public static Fact amountOf(String labelKey, float value, float total){
        return new Fact(labelKey, Format.amountOf, value, total, null, null, null, Tone.normal, false);
    }

    public static Fact text(String labelKey, String text){
        return new Fact(labelKey, Format.text, 0f, -1f, Objects.requireNonNull(text, "text"), null, null, Tone.normal, false);
    }

    /** A fact naming a game resource, shown with that resource's icon. */
    public static Fact resource(String labelKey, ResourceRef resource){
        return new Fact(labelKey, Format.text, 0f, -1f, resource.name, resource.kind, resource.id, Tone.normal, false);
    }

    public Fact tone(Tone tone){
        return new Fact(labelKey, format, value, total, text, iconKind, iconId, tone, estimate);
    }

    public Fact estimate(){
        return new Fact(labelKey, format, value, total, text, iconKind, iconId, tone, true);
    }

    /** Whether the numbers carried by this fact can be printed; a fact with NaN or infinity cannot. */
    public boolean printable(){
        if(format == Format.text) return text != null && !text.isBlank();
        boolean valueOk = !Float.isNaN(value) && !Float.isInfinite(value);
        boolean totalOk = format != Format.amountOf || (!Float.isNaN(total) && !Float.isInfinite(total));
        return valueOk && totalOk;
    }

    @Override
    public String toString(){
        return labelKey + "=" + (format == Format.text ? text : value + (format == Format.amountOf ? "/" + total : ""))
            + (estimate ? "~" : "");
    }
}
