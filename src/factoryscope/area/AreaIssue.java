package factoryscope.area;

import factoryscope.analysis.*;
import factoryscope.model.*;

import java.util.*;

/** Identity of one kind of problem: a reason, and the resource it is about when there is one. */
public final class AreaIssue{
    public final DiagnosticReason reason;
    /** Null when the reason names no resource, as for a disabled building or a power shortfall. */
    public final ResourceRef resource;

    public AreaIssue(DiagnosticReason reason, ResourceRef resource){
        this.reason = Objects.requireNonNull(reason, "reason");
        this.resource = resource;
    }

    /**
     * Stable grouping key, built from content identity rather than from anything the player reads.
     * Two locales must aggregate the same factory into the same issues.
     */
    public String key(){
        return reason.name() + "|" + (resource == null ? "" : resource.key());
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof AreaIssue issue)) return false;
        return reason == issue.reason && Objects.equals(resource, issue.resource);
    }

    @Override
    public int hashCode(){
        return key().hashCode();
    }

    @Override
    public String toString(){
        return key();
    }
}
