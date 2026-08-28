package factoryscope.model;

import java.util.*;

/**
 * A resource a finding is about, kept as identity plus display name rather than display name alone.
 *
 * <p>The distinction matters as soon as findings are aggregated across many buildings: grouping by
 * {@link #name} would mean that switching the game to another language changes which shortages are
 * considered the same shortage, and that two modded items with the same display name are merged.
 * {@link #id} is the internal Mindustry content name ({@code "sand"}, {@code "water"}), which is what
 * {@link #key()} groups on.
 */
public final class ResourceRef{
    public final ResourceKind kind;
    /**
     * Internal Mindustry content id, or null for a consumer with no content behind it - "any accepted
     * item", or a consumer type FactoryScope does not recognise.
     */
    public final String id;
    /** Localized name, for display only. */
    public final String name;

    public ResourceRef(ResourceKind kind, String id, String name){
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Convenience for a resource with no content identity, such as power or an unknown consumer. */
    public static ResourceRef unidentified(ResourceKind kind, String name){
        return new ResourceRef(kind, null, name);
    }

    /**
     * Stable grouping key.
     *
     * <p>Falls back to the name only when there is no content id at all, which happens for consumers
     * that name themselves after their own type rather than after a resource. Those names are not
     * translated, so the key stays stable there too.
     */
    public String key(){
        return kind.name() + ":" + (id != null ? id : "?" + name);
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof ResourceRef ref)) return false;
        return kind == ref.kind && Objects.equals(id, ref.id) && (id != null || name.equals(ref.name));
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
