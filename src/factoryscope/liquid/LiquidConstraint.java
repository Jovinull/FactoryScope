package factoryscope.liquid;

import factoryscope.model.*;

import java.util.*;

/** Resource constraint on one structural liquid route. */
public final class LiquidConstraint{
    private enum Kind{any, only, oneOf}

    private static final LiquidConstraint ANY = new LiquidConstraint(Kind.any, null, Set.of());
    private final Kind kind;
    private final ResourceRef liquid;
    private final Set<ResourceRef> liquids;

    private LiquidConstraint(Kind kind, ResourceRef liquid, Collection<ResourceRef> liquids){
        this.kind = kind;
        this.liquid = liquid;
        TreeSet<ResourceRef> ordered = new TreeSet<>(Comparator.comparing(ResourceRef::key));
        ordered.addAll(liquids);
        this.liquids = Collections.unmodifiableSet(ordered);
    }

    public static LiquidConstraint any(){ return ANY; }

    public static LiquidConstraint only(ResourceRef liquid){
        requireLiquid(liquid);
        return new LiquidConstraint(Kind.only, liquid, Set.of());
    }

    public static LiquidConstraint oneOf(Collection<ResourceRef> liquids){
        Objects.requireNonNull(liquids, "liquids");
        if(liquids.isEmpty()) throw new IllegalArgumentException("at least one liquid is required");
        for(ResourceRef liquid : liquids) requireLiquid(liquid);
        return liquids.size() == 1 ? only(liquids.iterator().next())
            : new LiquidConstraint(Kind.oneOf, null, liquids);
    }

    public boolean allows(ResourceRef candidate){
        if(candidate == null || candidate.kind != ResourceKind.liquid || candidate.id == null) return false;
        return switch(kind){
            case any -> true;
            case only -> liquid.equals(candidate);
            case oneOf -> liquids.contains(candidate);
        };
    }

    public boolean intersects(LiquidConstraint other, ResourceRef candidate){
        return allows(candidate) && other.allows(candidate);
    }

    public LiquidConstraint intersection(LiquidConstraint other){
        Objects.requireNonNull(other, "other");
        if(kind == Kind.any) return other;
        if(other.kind == Kind.any) return this;
        Set<ResourceRef> left = values(), right = other.values();
        left.retainAll(right);
        return left.isEmpty() ? null : oneOf(left);
    }

    public LiquidConstraint union(LiquidConstraint other){
        Objects.requireNonNull(other, "other");
        if(kind == Kind.any || other.kind == Kind.any) return any();
        Set<ResourceRef> combined = values();
        combined.addAll(other.values());
        return oneOf(combined);
    }

    private Set<ResourceRef> values(){
        Set<ResourceRef> result = new HashSet<>();
        if(kind == Kind.only) result.add(liquid);
        else if(kind == Kind.oneOf) result.addAll(liquids);
        return result;
    }

    private static void requireLiquid(ResourceRef liquid){
        Objects.requireNonNull(liquid, "liquid");
        if(liquid.kind != ResourceKind.liquid || liquid.id == null){
            throw new IllegalArgumentException("a liquid constraint requires an identified liquid");
        }
    }

    @Override
    public String toString(){
        return switch(kind){
            case any -> "any-liquid";
            case only -> "only:" + liquid.key();
            case oneOf -> "oneOf:" + liquids.stream().map(ResourceRef::key).toList();
        };
    }
}
