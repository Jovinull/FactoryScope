package factoryscope.ui;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import factoryscope.model.*;
import mindustry.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * Resolves an icon for a resource from the content id the probe recorded.
 *
 * <p>The lookup goes through {@code Vars.content}, so modded items and liquids resolve exactly the way
 * vanilla ones do and no table of known content is needed. Content that cannot be resolved simply gets
 * a neutral placeholder rather than nothing, which keeps rows aligned.
 */
public final class ContentIcons{
    public static final float SIZE = 24f;

    private ContentIcons(){
    }

    public static TextureRegion region(ResourceKind kind, String contentId){
        if(contentId == null) return null;
        UnlockableContent content = switch(kind){
            case item -> Vars.content.item(contentId);
            case liquid -> Vars.content.liquid(contentId);
            default -> null;
        };
        return content == null ? null : content.uiIcon;
    }

    public static void add(Table table, ResourceKind kind, String contentId, float size){
        TextureRegion region = region(kind, contentId);
        if(region != null){
            table.image(region).size(size).padRight(6f);
        }else if(kind == ResourceKind.power){
            table.image(Icon.power).size(size).color(Pal.power).padRight(6f);
        }else{
            table.image(Tex.whiteui).size(size).color(Color.clear).padRight(6f);
        }
    }

    public static void add(Table table, ResourceKind kind, String contentId){
        add(table, kind, contentId, SIZE);
    }
}
