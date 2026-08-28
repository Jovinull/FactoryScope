package factoryscope.area;

import factoryscope.analysis.*;

import java.util.*;

/** One issue and every distinct building it affects. */
public final class AreaIssueGroup{
    public final AreaIssue issue;
    /** How bad the worst instance of this issue is. */
    public final Severity severity;
    /** False when at least one contributing finding was itself a hedge; the wording must then hedge too. */
    public final boolean certain;
    /** Distinct affected buildings, in the order they were collected. Never contains a duplicate. */
    public final List<BuildingRef> buildings;

    AreaIssueGroup(AreaIssue issue, Severity severity, boolean certain, List<BuildingRef> buildings){
        this.issue = issue;
        this.severity = severity;
        this.certain = certain;
        this.buildings = List.copyOf(buildings);
    }

    /** Number of distinct buildings affected - never a number of findings. */
    public int buildingCount(){
        return buildings.size();
    }

    @Override
    public String toString(){
        return issue + " x" + buildingCount();
    }
}
