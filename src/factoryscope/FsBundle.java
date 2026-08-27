package factoryscope;

import arc.*;

/**
 * Every user-facing string goes through here.
 *
 * <p>Keys live in {@code assets/bundles/bundle.properties} and are prefixed with {@code factoryscope.},
 * so a translation is a matter of dropping a {@code bundle_xx.properties} next to it - no Java changes.
 * When the game bundle is not loaded (unit tests, headless), the key itself is returned rather than
 * throwing, because a missing string must never take the panel down.
 */
public final class FsBundle{
    public static final String PREFIX = "factoryscope.";

    private FsBundle(){
    }

    public static String get(String key){
        String full = PREFIX + key;
        try{
            return Core.bundle == null ? full : Core.bundle.get(full, full);
        }catch(RuntimeException e){
            return full;
        }
    }

    public static String format(String key, Object... args){
        String full = PREFIX + key;
        try{
            if(Core.bundle == null) return full;
            return Core.bundle.format(full, args);
        }catch(RuntimeException e){
            return full;
        }
    }

    /** Bundle reference for widgets that resolve their own text, e.g. {@code table.add("@factoryscope.x")}. */
    public static String ref(String key){
        return "@" + PREFIX + key;
    }
}
