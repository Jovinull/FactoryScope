package factoryscope.model;

/** Broad category of a factory input or output, used to pick icons and priority. */
public enum ResourceKind{
    item,
    liquid,
    power,
    /** A unit type: what a unit factory produces, or a unit a block takes in as payload. */
    unit,
    /** A block carried as payload, such as the walls a unit assembler consumes. */
    block,
    /** A consumer whose resource semantics FactoryScope does not recognise. */
    other
}
