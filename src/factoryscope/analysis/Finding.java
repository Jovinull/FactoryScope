package factoryscope.analysis;

import factoryscope.model.*;

import java.util.*;

/** One conclusion about a factory: a reason, how bad it is, and which resources it concerns. */
public final class Finding{
    public final DiagnosticReason reason;
    public final Severity severity;
    /**
     * Resources this finding is about; empty when the reason needs no subject.
     *
     * <p>Each entry carries content identity as well as a display name, so aggregation across
     * buildings can group on something that does not change with the game language.
     */
    public final List<ResourceRef> resources;
    /**
     * False when the underlying evidence is not strong enough to state the cause as fact. The
     * presentation layer must hedge such findings instead of asserting them.
     */
    public final boolean certain;

    public Finding(DiagnosticReason reason, Severity severity, List<ResourceRef> resources, boolean certain){
        this.reason = reason;
        this.severity = severity;
        this.resources = List.copyOf(resources);
        this.certain = certain;
    }

    public static Finding of(DiagnosticReason reason, Severity severity){
        return new Finding(reason, severity, List.of(), true);
    }

    public static Finding of(DiagnosticReason reason, Severity severity, List<ResourceRef> resources){
        return new Finding(reason, severity, resources, true);
    }

    public static Finding uncertain(DiagnosticReason reason, Severity severity, List<ResourceRef> resources){
        return new Finding(reason, severity, resources, false);
    }

    /** Display names of {@link #resources}, in order. */
    public List<String> resourceNames(){
        List<String> names = new ArrayList<>(resources.size());
        for(ResourceRef ref : resources) names.add(ref.name);
        return names;
    }

    @Override
    public String toString(){
        return reason + "/" + severity + (resources.isEmpty() ? "" : resources.toString());
    }
}
