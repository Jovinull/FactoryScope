package factoryscope.model;

/**
 * How much FactoryScope actually understands about the inspected building.
 * The diagnostic engine refuses to make block-specific claims below {@link #full}.
 */
public enum SupportLevel{
    /** A GenericCrafter (or subclass): consumers, craft time and outputs are all known. */
    full,
    /** An ordinary building with standard consumers, but no known production model. */
    basic,
    /** A building with no consumers FactoryScope can reason about. */
    minimal
}
