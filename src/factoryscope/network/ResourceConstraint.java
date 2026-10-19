package factoryscope.network;

import factoryscope.model.*;

import java.util.*;

/** Resources which may use one structural route. */
public final class ResourceConstraint{
    private enum Kind{ anyItems, anyPayloads, only, oneOf, except, anyOf }

    private static final ResourceConstraint ANY_ITEMS = new ResourceConstraint(Kind.anyItems, null);
    private static final ResourceConstraint ANY_PAYLOADS = new ResourceConstraint(Kind.anyPayloads, null);
    private final Kind kind;
    private final ResourceRef resource;
    private final Set<ResourceRef> resources;
    private final List<ResourceConstraint> alternatives;

    private ResourceConstraint(Kind kind, ResourceRef resource){
        this.kind = kind;
        this.resource = resource;
        this.resources = Set.of();
        this.alternatives = List.of();
    }

    private ResourceConstraint(Collection<ResourceRef> resources){
        this.kind = Kind.oneOf;
        this.resource = null;
        this.alternatives = List.of();
        TreeMap<String, ResourceRef> ordered = new TreeMap<>();
        for(ResourceRef resource : resources){
            requireTraceable(resource);
            ordered.put(resource.key(), resource);
        }
        if(ordered.isEmpty()) throw new IllegalArgumentException("at least one resource is required");
        this.resources = Collections.unmodifiableSet(new LinkedHashSet<>(ordered.values()));
    }

    private ResourceConstraint(Collection<ResourceConstraint> alternatives, boolean union){
        this.kind = Kind.anyOf;
        this.resource = null;
        this.resources = Set.of();
        List<ResourceConstraint> ordered = new ArrayList<>(alternatives);
        ordered.sort(Comparator.comparing(ResourceConstraint::toString));
        if(ordered.isEmpty()) throw new IllegalArgumentException("at least one constraint is required");
        this.alternatives = List.copyOf(ordered);
    }

    /** Item routes are the default route type used by the original item network. */
    public static ResourceConstraint any(){ return ANY_ITEMS; }
    public static ResourceConstraint anyItems(){ return ANY_ITEMS; }
    public static ResourceConstraint anyPayloads(){ return ANY_PAYLOADS; }
    public static ResourceConstraint only(ResourceRef resource){
        requireTraceable(resource);
        return new ResourceConstraint(Kind.only, resource);
    }
    public static ResourceConstraint oneOf(Collection<ResourceRef> resources){
        return new ResourceConstraint(Objects.requireNonNull(resources, "resources"));
    }
    public static ResourceConstraint except(ResourceRef resource){
        requireTraceable(resource);
        return new ResourceConstraint(Kind.except, resource);
    }
    public static ResourceConstraint anyOf(Collection<ResourceConstraint> constraints){
        Objects.requireNonNull(constraints, "constraints");
        List<ResourceConstraint> values = new ArrayList<>(constraints);
        if(values.isEmpty()) throw new IllegalArgumentException("at least one constraint is required");
        if(values.stream().anyMatch(constraint -> constraint.kind == Kind.anyItems)
            && values.stream().allMatch(constraint -> constraint.kind == Kind.anyItems)) return anyItems();
        if(values.stream().anyMatch(constraint -> constraint.kind == Kind.anyPayloads)
            && values.stream().allMatch(constraint -> constraint.kind == Kind.anyPayloads || isPayloadConstraint(constraint))) return anyPayloads();
        return values.size() == 1 ? values.get(0) : new ResourceConstraint(values, true);
    }

    public boolean allows(ResourceRef candidate){
        if(!isTraceable(candidate)) return false;
        return switch(kind){
            case anyItems -> candidate.kind == ResourceKind.item;
            case anyPayloads -> isPayload(candidate.kind);
            case only -> resource.equals(candidate);
            case oneOf -> resources.contains(candidate);
            case except -> !resource.equals(candidate);
            case anyOf -> alternatives.stream().anyMatch(constraint -> constraint.allows(candidate));
        };
    }

    private static boolean isPayloadConstraint(ResourceConstraint constraint){
        return switch(constraint.kind){
            case anyPayloads -> true;
            case only, except -> isPayload(constraint.resource.kind);
            case oneOf -> constraint.resources.stream().allMatch(resource -> isPayload(resource.kind));
            case anyOf -> constraint.alternatives.stream().allMatch(ResourceConstraint::isPayloadConstraint);
            case anyItems -> false;
        };
    }

    private static boolean isPayload(ResourceKind kind){ return kind == ResourceKind.unit || kind == ResourceKind.block; }
    private static boolean isTraceable(ResourceRef resource){
        return resource != null && resource.id != null
            && (resource.kind == ResourceKind.item || isPayload(resource.kind));
    }
    private static void requireTraceable(ResourceRef resource){
        if(!isTraceable(resource)) throw new IllegalArgumentException("resource must be an identified item, unit, or block");
    }

    @Override
    public String toString(){
        return switch(kind){
            case anyItems -> "anyItems";
            case anyPayloads -> "anyPayloads";
            case only -> "only:" + resource.key();
            case oneOf -> "oneOf:" + resources.stream().map(ResourceRef::key).toList();
            case except -> "except:" + resource.key();
            case anyOf -> "anyOf:" + alternatives;
        };
    }
}
