package factoryscope.network;

import factoryscope.model.*;

import java.util.*;

/** The item types which may use one structural route. */
public final class ItemConstraint{
    private enum Kind{ any, only, except }

    private static final ItemConstraint ANY = new ItemConstraint(Kind.any, null);
    private final Kind kind;
    private final ResourceRef item;

    private ItemConstraint(Kind kind, ResourceRef item){
        this.kind = kind;
        this.item = item;
    }

    public static ItemConstraint any(){ return ANY; }
    public static ItemConstraint only(ResourceRef item){ return new ItemConstraint(Kind.only, Objects.requireNonNull(item, "item")); }
    public static ItemConstraint except(ResourceRef item){ return new ItemConstraint(Kind.except, Objects.requireNonNull(item, "item")); }

    public boolean allows(ResourceRef candidate){
        if(candidate == null || candidate.kind != ResourceKind.item) return false;
        return switch(kind){
            case any -> true;
            case only -> item.equals(candidate);
            case except -> !item.equals(candidate);
        };
    }

    @Override
    public String toString(){ return kind + (item == null ? "" : ":" + item.key()); }
}
