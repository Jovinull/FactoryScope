package factoryscope.model;

/** Broad category of a factory input or output, used to pick icons and priority. */
public enum ResourceKind{
    item,
    liquid,
    power,
    /** A consumer whose resource semantics FactoryScope does not recognise. */
    other
}
