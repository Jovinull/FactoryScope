package factoryscope.network;

import factoryscope.model.*;

import java.util.*;

/** The item types which may use one structural route. */
public final class ItemConstraint{
    private enum Kind{ any, only, oneOf, except, anyOf }

    private static final ItemConstraint ANY = new ItemConstraint(Kind.any, null);
    private final Kind kind;
    private final ResourceRef item;
    private final Set<ResourceRef> items;
    private final List<ItemConstraint> alternatives;

    private ItemConstraint(Kind kind, ResourceRef item){
        this.kind = kind;
        this.item = item;
        this.items = Set.of();
        this.alternatives = List.of();
    }

    private ItemConstraint(Collection<ResourceRef> items){
        this.kind = Kind.oneOf;
        this.item = null;
        this.alternatives = List.of();
        TreeSet<ResourceRef> ordered = new TreeSet<>(Comparator.comparing(ResourceRef::key));
        ordered.addAll(items);
        if(ordered.isEmpty()) throw new IllegalArgumentException("at least one item is required");
        this.items = Collections.unmodifiableSet(ordered);
    }

    private ItemConstraint(Collection<ItemConstraint> alternatives, boolean union){
        this.kind = Kind.anyOf;
        this.item = null;
        this.items = Set.of();
        List<ItemConstraint> ordered = new ArrayList<>(alternatives);
        ordered.sort(Comparator.comparing(ItemConstraint::toString));
        if(ordered.isEmpty()) throw new IllegalArgumentException("at least one constraint is required");
        this.alternatives = List.copyOf(ordered);
    }

    public static ItemConstraint any(){ return ANY; }
    public static ItemConstraint only(ResourceRef item){ return new ItemConstraint(Kind.only, Objects.requireNonNull(item, "item")); }
    public static ItemConstraint oneOf(Collection<ResourceRef> items){ return new ItemConstraint(Objects.requireNonNull(items, "items")); }
    public static ItemConstraint except(ResourceRef item){ return new ItemConstraint(Kind.except, Objects.requireNonNull(item, "item")); }
    public static ItemConstraint anyOf(Collection<ItemConstraint> constraints){
        Objects.requireNonNull(constraints, "constraints");
        List<ItemConstraint> values = new ArrayList<>(constraints);
        if(values.stream().anyMatch(constraint -> constraint.kind == Kind.any)) return any();
        return values.size() == 1 ? values.get(0) : new ItemConstraint(values, true);
    }

    public boolean allows(ResourceRef candidate){
        if(candidate == null || candidate.kind != ResourceKind.item) return false;
        return switch(kind){
            case any -> true;
            case only -> item.equals(candidate);
            case oneOf -> items.contains(candidate);
            case except -> !item.equals(candidate);
            case anyOf -> alternatives.stream().anyMatch(constraint -> constraint.allows(candidate));
        };
    }

    @Override
    public String toString(){
        return switch(kind){
            case any -> "any";
            case only -> "only:" + item.key();
            case oneOf -> "oneOf:" + items.stream().map(ResourceRef::key).toList();
            case except -> "except:" + item.key();
            case anyOf -> "anyOf:" + alternatives;
        };
    }
}
