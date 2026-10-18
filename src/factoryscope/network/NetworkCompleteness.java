package factoryscope.network;

/** Whether every item transport block in the selected area had explicit topology support. */
public enum NetworkCompleteness{
    complete,
    partialUnsupportedTransport,
    /** A live snapshot read failed unexpectedly; see {@link ItemNetwork#probeFailures}. */
    partialProbeFailure
}
