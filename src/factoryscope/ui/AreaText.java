package factoryscope.ui;

import arc.graphics.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import mindustry.world.meta.*;

/**
 * Words and colours for area results.
 *
 * <p>Presentation is the only place a localized name is allowed to appear: everything that decides
 * which observations are the same observation works on content identity, in {@link AreaAnalyzer}.
 */
public final class AreaText{
    private AreaText(){
    }

    public static String status(AreaStatus status){
        return FsBundle.get("area.status." + status.slug());
    }

    public static Color color(AreaStatus status){
        return switch(status){
            case operating -> BlockStatus.active.color;
            case itemShortage, liquidShortage, powerLimited, otherLimitation -> BlockStatus.noInput.color;
            case outputBlocked -> BlockStatus.noOutput.color;
            case disabled, inoperable -> BlockStatus.logicDisable.color;
            case notConsuming, causeUnclear, limitedDiagnostics -> BlockStatus.inactive.color;
        };
    }

    /**
     * Headline for an issue group, e.g. "Sand shortage".
     *
     * <p>Only the reasons that are genuinely about a resource take its name; "power limited" reads
     * worse, not better, with the word "Power" spliced into it.
     */
    private static String issue(AreaIssue issue){
        String key = "area.issue." + issue.reason.slug();
        return issue.resource != null && namesResource(issue.reason)
            ? FsBundle.format(key + ".resource", issue.resource.name)
            : FsBundle.get(key);
    }

    /** Wording for an issue whose evidence was not strong enough to assert outright. */
    public static String issueTitle(AreaIssueGroup group){
        String title = issue(group.issue);
        return group.certain ? title : FsBundle.format("diagnosis.uncertain", title);
    }

    public static Color color(Severity severity){
        return Diagnostics.color(severity);
    }

    private static boolean namesResource(DiagnosticReason reason){
        return switch(reason){
            case missingItemInput, missingLiquidInput, outputBlocked, otherConsumerLimited -> true;
            default -> false;
        };
    }
}
