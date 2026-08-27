package factoryscope.analysis;

/** How badly a finding affects the factory. */
public enum Severity{
    /** Production is completely stopped. */
    stopped,
    /** Production continues, but below full speed. */
    reduced,
    /** Nothing wrong. */
    normal
}
