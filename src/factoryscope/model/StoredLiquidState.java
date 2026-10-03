package factoryscope.model;

import java.util.Objects;

/** One positive liquid amount captured from a building module at snapshot time. */
public final class StoredLiquidState{
    public final ResourceRef liquid;
    public final float amount;
    public final float capacity;

    public StoredLiquidState(ResourceRef liquid, float amount, float capacity){
        this.liquid = Objects.requireNonNull(liquid, "liquid");
        if(liquid.kind != ResourceKind.liquid || liquid.id == null) throw new IllegalArgumentException("liquid identity required");
        this.amount = amount;
        this.capacity = capacity;
    }
}
