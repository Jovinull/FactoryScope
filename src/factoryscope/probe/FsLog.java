package factoryscope.probe;

import arc.util.*;

import java.util.*;

/**
 * Rate-limited logging.
 *
 * <p>Analysis runs while the panel is open, so a single misbehaving block would otherwise write the
 * same stack trace to the log several times a second.
 */
public final class FsLog{
    private static final long REPEAT_INTERVAL_MS = 60_000L;
    private static final Map<String, Long> lastLogged = new HashMap<>();

    private FsLog(){
    }

    /** Logs {@code message} at most once per minute per key, together with the cause. */
    public static void warnOnce(String key, String message, Throwable cause){
        long now = System.currentTimeMillis();
        Long previous = lastLogged.get(key);
        if(previous != null && now - previous < REPEAT_INTERVAL_MS) return;
        lastLogged.put(key, now);
        Log.err("[FactoryScope] " + message, cause);
    }

    public static void info(String message){
        Log.info("[FactoryScope] " + message);
    }

    /** Clears the throttling state; called when a world is unloaded so a new session logs afresh. */
    public static void reset(){
        lastLogged.clear();
    }
}
