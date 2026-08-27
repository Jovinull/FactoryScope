package factoryscope;

import arc.*;
import factoryscope.probe.*;
import factoryscope.ui.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

/**
 * Mod entry point.
 *
 * <p>FactoryScope is a client-side diagnostic tool: it adds no content and changes no game state. The
 * constructor therefore does nothing but register a listener, and all user interface work waits for
 * {@link ClientLoadEvent}, at which point {@code Vars.ui} exists. Touching the interface any earlier
 * would break every headless context (dedicated servers, tooling) that merely loads the mod.
 */
public class FactoryScope extends Mod{

    public FactoryScope(){
        Events.on(ClientLoadEvent.class, event -> {
            try{
                FactoryScopeUI.init();
                FsLog.info(version() + " inspector ready");
            }catch(Exception e){
                //a broken interface must not take the game down with it
                FsLog.warnOnce("init", "failed to initialise the inspector interface", e);
            }
        });
    }

    @Override
    public void init(){
        if(Vars.headless){
            FsLog.info("headless mode detected, the inspector interface stays disabled");
        }
    }

    /** Reads the version straight from mod.hjson so there is only ever one place to change it. */
    public static String version(){
        var mod = Vars.mods == null ? null : Vars.mods.getMod(FactoryScope.class);
        return mod == null || mod.meta == null || mod.meta.version == null ? "dev" : mod.meta.version;
    }
}
