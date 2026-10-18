package factoryscope.model;

/**
 * Which production model FactoryScope applied to a building.
 *
 * <p>Each family has its own rules in Mindustry, and FactoryScope only reports rates, blocked outputs and
 * other production verdicts for a family whose rules were taken from the engine source and checked
 * against the running engine. {@link #none} means no such model exists for the block.
 */
public enum ProductionFamily{
    none,
    /** {@code GenericCrafter} and its subclasses: fixed inputs, cycle time and outputs. */
    crafter,
    /** {@code Separator}: a fixed cycle whose single output item is drawn at random. */
    separator,
    /** {@code Drill}, {@code BurstDrill}: mine the ore under the block. */
    drill,
    /** {@code BeamDrill}: mines ore walls in front of the block. */
    beamDrill,
    /** {@code WallCrafter}: crushes suitable walls in front of the block. */
    wallCrafter,
    /** {@code Pump}: pumps the liquid under the block. */
    pump,
    /** {@code SolidPump}, {@code Fracker}: extract a fixed liquid from the ground. */
    solidPump,
    /** {@code PowerGenerator} and its subclasses. */
    generator,
    /** {@code UnitFactory}: builds the selected unit from items. */
    unitFactory,
    /** {@code Reconstructor}: upgrades a unit carried into it. */
    reconstructor,
    /** {@code UnitAssembler}: assembles a unit from payloads with its drones. */
    unitAssembler,
    /** {@code BaseTurret} and its subclasses. */
    turret;

    public boolean modeled(){
        return this != none;
    }
}
