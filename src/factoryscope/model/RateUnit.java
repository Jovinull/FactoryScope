package factoryscope.model;

/** Unit attached to a numeric requirement, so the UI never has to guess. */
public enum RateUnit{
    /** No meaningful amount is known. */
    none,
    /** Amount needed for one production cycle. */
    perCraft,
    /** Amount needed per second of operation. */
    perSecond
}
