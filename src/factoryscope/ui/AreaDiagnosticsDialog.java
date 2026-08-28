package factoryscope.ui;

import arc.func.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.meta.*;

/**
 * The area report.
 *
 * <h2>Snapshot, not a live feed</h2>
 * The report is taken once, when the selection is released, and refreshed only when the player asks.
 * Probing hundreds of buildings every frame would be both wasteful and dishonest - the numbers would
 * flicker faster than they can be read, and none of them would correspond to a single instant.
 *
 * <h2>What it does not claim</h2>
 * The issue list counts buildings that report the same problem. It does not say which building caused
 * it: FactoryScope has no model of how factories feed each other, so "sand shortages affect eight
 * buildings" is as far as the evidence goes.
 */
public final class AreaDiagnosticsDialog extends BaseDialog{
    /** Enough to see the shape of a problem; beyond this the list is a wall rather than information. */
    private static final int MAX_LISTED_BUILDINGS = 40;

    private final Runnable onSelectAnother;
    private final Cons<Building> onInspect;

    private Table body;
    private AreaSelection selection;
    private AreaDiagnosticResult result;

    public AreaDiagnosticsDialog(Runnable onSelectAnother, Cons<Building> onInspect){
        super("");
        this.onSelectAnother = onSelectAnother;
        this.onInspect = onInspect;

        title.setText(FsBundle.get("area.title"));
        cont.pane(table -> body = table).grow().with(pane -> pane.setScrollingDisabled(true, false));

        buttons.button(FsBundle.ref("area.refresh"), Icon.refresh, this::refresh)
            .size(190f, 64f).name("factoryscope-area-refresh");
        buttons.button(FsBundle.ref("area.select-another"), Icon.grid, this::selectAnother)
            .size(230f, 64f).name("factoryscope-area-select");
        addCloseButton();
    }

    /** Shows the report for an area that has just been scanned. */
    public void show(AreaSelection area, AreaDiagnosticResult scanned){
        this.selection = area;
        this.result = scanned;
        rebuild();
        show();
    }

    /** True while a report is on screen. */
    public boolean showing(){
        return isShown() && result != null;
    }

    /** The report on screen right now, or null when the dialog is closed or was cleared. */
    public AreaDiagnosticResult result(){
        return isShown() ? result : null;
    }

    public AreaSelection selection(){
        return selection;
    }

    /**
     * Re-runs the whole pipeline over the same bounds.
     *
     * <p>Deliberately a fresh spatial query and fresh snapshots rather than a re-read of the buildings
     * already known: between two refreshes a factory can be built, destroyed or replaced, and a report
     * that only updated the numbers of the buildings it happened to see first would be wrong about
     * exactly the thing the player is watching.
     */
    public void refresh(){
        if(selection == null) return;
        if(!Vars.state.isGame()){
            clear();
            hide();
            return;
        }
        result = AreaProbe.scan(selection, viewerTeam());
        rebuild();
    }

    /** Drops the report and closes; called when the world changes underneath it. */
    public void clear(){
        selection = null;
        result = null;
        if(body != null) body.clear();
        if(isShown()) hide();
    }

    private void selectAnother(){
        hide();
        onSelectAnother.run();
    }

    private static Team viewerTeam(){
        return Vars.player == null ? null : Vars.player.team();
    }

    // ------------------------------------------------------------------ layout

    private void rebuild(){
        if(body == null) return;
        body.clear();
        body.top().defaults().growX().left();
        if(result == null || selection == null) return;

        buildSummary();
        if(result.empty()){
            buildEmptyState();
        }else{
            buildIssues();
        }
    }

    private void buildSummary(){
        AreaSummary summary = result.summary;

        section("area.section.summary");
        panel(table -> {
            value(table, FsBundle.get("area.selected"), Integer.toString(summary.selected), Pal.accent);
            value(table, FsBundle.get("area.production"), Integer.toString(summary.production), Pal.lightishGray);
            value(table, FsBundle.get("area.size"),
                FsBundle.format("area.size-value", selection.width(), selection.height()), Pal.lightishGray);
            if(summary.skipped() > 0){
                value(table, FsBundle.get("area.skipped"), Integer.toString(summary.skipped()), Pal.lightOrange);
            }
        });

        if(summary.analyzed == 0) return;

        section("area.section.status");
        panel(table -> summary.byStatus.forEach((status, count) -> table.table(row -> {
            row.add(Integer.toString(count)).color(AreaText.color(status)).width(56f).right().padRight(10f);
            row.add(AreaText.status(status)).color(AreaText.color(status))
                .growX().left().ellipsis(true).minWidth(0f);
        }).growX().padBottom(2f).row()));
    }

    private void buildEmptyState(){
        panel(table -> table.labelWrap(FsBundle.get("area.none")).color(Pal.lightOrange).growX());
    }

    private void buildIssues(){
        section("area.section.issues");

        if(result.issues.isEmpty()){
            //the honest wording: nothing in the area reports a problem, which is not the same as the
            //production network being sound - FactoryScope cannot see the network at all
            panel(table -> table.labelWrap(FsBundle.get("area.no-problems"))
                .color(BlockStatus.active.color).growX());
            return;
        }

        panel(table -> {
            for(AreaIssueGroup group : result.issues) issueRow(table, group);
            table.labelWrap(FsBundle.get("area.issue-note")).color(Pal.gray).growX().padTop(8f).row();
        });
    }

    /**
     * One collapsible issue group. The affected-building rows are built the first time the group is
     * expanded: a large selection can produce hundreds of them, and none are worth constructing until
     * somebody asks to see them.
     */
    private void issueRow(Table table, AreaIssueGroup group){
        Table listing = new Table();
        Collapser collapser = new Collapser(listing, true);
        collapser.setDuration(0.15f);
        boolean[] filled = {false};

        table.button(row -> {
            row.left().margin(4f);
            ContentIcons.add(row, kindOf(group), idOf(group), 22f);
            row.add(AreaText.issueTitle(group)).color(AreaText.color(group.severity))
                .growX().left().ellipsis(true).minWidth(0f);
            row.add(FsBundle.format("area.affected", group.buildingCount()))
                .color(Pal.lightishGray).right().padLeft(8f);
        }, Styles.flatt, () -> {
            if(!filled[0]){
                fillBuildings(listing, group);
                filled[0] = true;
            }
            collapser.setCollapsed(!collapser.isCollapsed());
        }).growX().pad(2f).name("factoryscope-area-issue").row();

        table.add(collapser).growX().padLeft(12f).row();
    }

    private void fillBuildings(Table listing, AreaIssueGroup group){
        listing.left().defaults().growX().left();
        int shown = Math.min(group.buildingCount(), MAX_LISTED_BUILDINGS);

        for(int i = 0; i < shown; i++){
            BuildingRef ref = group.buildings.get(i);
            listing.table(row -> {
                row.button(inner -> {
                    inner.left().margin(2f);
                    inner.add(ref.blockName).growX().left().ellipsis(true).minWidth(0f);
                    inner.add(FsBundle.format("area.coordinates", ref.tileX, ref.tileY))
                        .color(Pal.gray).right().padLeft(8f);
                }, Styles.flatt, () -> inspect(ref)).growX().name("factoryscope-area-building");

                row.button(Icon.zoomSmall, Styles.emptyi, () -> locate(ref)).size(34f).padLeft(4f)
                    .tooltip(FsBundle.ref("area.locate"));
            }).growX().padBottom(2f).row();
        }

        if(shown < group.buildingCount()){
            //truncation is stated, never silent
            listing.add(FsBundle.format("area.listing-truncated", shown, group.buildingCount()))
                .color(Pal.lightOrange).padTop(4f).left().row();
        }
    }

    // ------------------------------------------------------------------ navigation

    private void inspect(BuildingRef ref){
        Building build = AreaProbe.resolve(ref);
        if(build == null){
            Vars.ui.showInfoToast(FsBundle.get("area.building-gone"), 2f);
            return;
        }
        //the report stays open underneath, so closing the building panel comes straight back to it
        onInspect.get(build);
    }

    private void locate(BuildingRef ref){
        Building build = AreaProbe.resolve(ref);
        if(build == null){
            Vars.ui.showInfoToast(FsBundle.get("area.building-gone"), 2f);
            return;
        }
        //panCamera is the game's own way of moving the view; no unit is moved and no state is written
        hide();
        if(Vars.control != null && Vars.control.input != null){
            Vars.control.input.panCamera(new Vec2(build.x, build.y));
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static ResourceKind kindOf(AreaIssueGroup group){
        return group.issue.resource == null ? ResourceKind.other : group.issue.resource.kind;
    }

    private static String idOf(AreaIssueGroup group){
        return group.issue.resource == null ? null : group.issue.resource.id;
    }

    private void section(String key){
        body.add(FsBundle.get(key)).color(Pal.accent).padTop(10f).padBottom(2f).left().row();
        //Image.draw() skips a null drawable entirely, so the rule needs a real one
        body.image(Tex.whiteui).height(3f).color(Pal.accent).growX().padBottom(4f).row();
    }

    private void panel(Cons<Table> builder){
        body.table(Tex.pane, table -> {
            table.margin(8f).top().defaults().growX().left();
            builder.get(table);
        }).growX().row();
    }

    private void value(Table table, String label, String text, Color color){
        table.table(row -> {
            row.add(label).growX().left().ellipsis(true).minWidth(0f);
            row.add(text).color(color).right();
        }).growX().padBottom(2f).row();
    }
}
