package factoryscope.ui;

import arc.graphics.*;
import factoryscope.*;
import factoryscope.analysis.*;
import mindustry.world.meta.*;

import java.util.*;

/**
 * Maps diagnostic results onto the words and colours the player sees.
 *
 * <p>Colours are borrowed from {@link BlockStatus} so a FactoryScope verdict reads the same as the
 * status dot the game already draws over the block.
 */
public final class Diagnostics{
    private Diagnostics(){
    }

    /** Short status headline, e.g. "Input shortage". */
    public static String status(DiagnosticReason reason){
        return FsBundle.get("status." + slug(reason));
    }

    /** Full sentence explaining the verdict, with the affected resources filled in. */
    public static String explanation(Finding finding){
        String key = "diagnosis." + slug(finding.reason) + "." + finding.severity.name();
        String text = finding.resources.isEmpty()
            ? FsBundle.get(key)
            : FsBundle.format(key, join(finding.resources));
        return finding.certain ? text : FsBundle.format("diagnosis.uncertain", text);
    }

    public static Color color(DiagnosticReason reason){
        return switch(reason){
            case active -> BlockStatus.active.color;
            case disabled -> BlockStatus.logicDisable.color;
            case outputBlocked -> BlockStatus.noOutput.color;
            case missingItemInput, missingLiquidInput, insufficientPower,
                 otherConsumerLimited, blockConditionLimited -> BlockStatus.noInput.color;
            case haltedUnknownCause, limitedSupport -> BlockStatus.inactive.color;
        };
    }

    public static Color color(Severity severity){
        return switch(severity){
            case normal -> BlockStatus.active.color;
            case reduced -> BlockStatus.noOutput.color;
            case stopped -> BlockStatus.noInput.color;
        };
    }

    /** Colour for an efficiency figure: green when full, orange when partial, red when stopped. */
    public static Color efficiencyColor(float efficiency){
        if(efficiency >= 0.999f) return BlockStatus.active.color;
        if(efficiency > 0f) return BlockStatus.noOutput.color;
        return BlockStatus.noInput.color;
    }

    private static String join(List<String> values){
        return String.join(", ", values);
    }

    /** {@code missingItemInput} to {@code missing-item-input}, so bundle keys stay readable. */
    private static String slug(DiagnosticReason reason){
        StringBuilder result = new StringBuilder(reason.name().length() + 4);
        for(char c : reason.name().toCharArray()){
            if(Character.isUpperCase(c)){
                result.append('-').append(Character.toLowerCase(c));
            }else{
                result.append(c);
            }
        }
        return result.toString();
    }
}
