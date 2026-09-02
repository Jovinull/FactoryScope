package factoryscope.ui;

import arc.*;
import arc.func.*;
import arc.graphics.*;
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
 * it: static topology shows possible item routes, not a cause of a current diagnostic condition.
 */
public final class AreaDiagnosticsDialog extends BaseDialog{
    /** Enough rows to see the shape of a problem; the rest are one button away. */
    private static final int PAGE = 40;
    /** Widest the report reads comfortably; wider windows get margins instead of stretched rows. */
    private static final float COLUMN_WIDTH = 620f;

    private final Runnable onSelectAnother;
    private final Cons<Building> onInspect;
    private final Cons<BuildingRef> onLocate;

    private Table body;
    private Cell<Table> bodyCell;
    private AreaSelection selection;
    private AreaDiagnosticResult result;
    private final NetworkDialog networkDialog = new NetworkDialog();

    public AreaDiagnosticsDialog(Runnable onSelectAnother, Cons<Building> onInspect, Cons<BuildingRef> onLocate){
        super("");
        this.onSelectAnother = onSelectAnother;
        this.onInspect = onInspect;
        this.onLocate = onLocate;

        title.setText(FsBundle.get("area.title"));
        networkDialog.setOnViewWorld(this::viewNetworkInWorld);
        //a column rather than the full width of the window: a count pinned to the far edge of a 4K
        //display is a long way from the label it belongs to
        cont.pane(outer -> {
            outer.top();
            bodyCell = outer.add(body = new Table()).top();
        }).grow().with(pane -> pane.setScrollingDisabled(true, false));

        buttons.button(FsBundle.ref("area.refresh"), Icon.refresh, this::refresh)
            .size(190f, 64f).name("factoryscope-area-refresh");
        buttons.button(FsBundle.ref("area.select-another"), Icon.grid, this::selectAnother)
            .size(250f, 64f).name("factoryscope-area-select");
        buttons.button(FsBundle.ref("network.open"), Icon.list, this::openNetwork)
            .size(180f, 64f).name("factoryscope-area-network");
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

    /**
     * True when a report is being held for the player to come back to, whether it is on screen or
     * temporarily out of the way while they look at the world.
     */
    public boolean hasReport(){
        return result != null && selection != null;
    }

    /** Puts a held report back on screen, exactly as it was. Nothing is re-scanned. */
    public void reopen(){
        if(!hasReport() || isShown()) return;
        rebuild();
        show();
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
        networkDialog.hide();
        if(body != null) body.clear();
        if(isShown()) hide();
    }

    private void selectAnother(){
        hide();
        onSelectAnother.run();
    }

    private void openNetwork(){
        if(result != null && result.network != null) networkDialog.show(result.network);
    }

    private void viewNetworkInWorld(){
        if(result == null || result.network == null) return;
        hide();
        FactoryScopeUI.viewNetworkInWorld(result.network, networkDialog.selected(), () -> {
            show();
            networkDialog.reopen();
        }, this::clear);
    }

    private static Team viewerTeam(){
        return Vars.player == null ? null : Vars.player.team();
    }

    // ------------------------------------------------------------------ layout

    private void rebuild(){
        if(body == null) return;
        body.clear();
        body.top().defaults().growX().left();
        //Cell.width is in design units and scales itself, so the room available has to be measured in
        //the same units rather than in raw pixels
        float available = Core.scene.getWidth() / Scl.scl() - 40f;
        bodyCell.width(Math.min(available, COLUMN_WIDTH));
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
            //deliberately not "production buildings": this counts the ones FactoryScope can put a rate
            //on, which is narrower than what a player would call a production building
            value(table, FsBundle.get("area.with-rates"), Integer.toString(summary.production), Pal.lightishGray);
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
            //an area of walls has no problems in the same sense that it has no production; saying
            //"nothing is wrong" there would read as a clean bill of health for something never examined
            boolean nothingDiagnosable = onlyLimitedSupport();
            panel(table -> table.labelWrap(FsBundle.get(nothingDiagnosable ? "area.only-limited" : "area.no-problems"))
                .color(nothingDiagnosable ? Pal.lightishGray : BlockStatus.active.color).growX());
            return;
        }

        panel(table -> {
            for(AreaIssueGroup group : result.issues) issueRow(table, group);
            table.labelWrap(FsBundle.get("area.issue-note")).color(Pal.gray).growX().padTop(8f).row();
        });
    }

    /** "1 building", not "1 buildings": the count is read far more often than it is skimmed. */
    private static String affected(int count){
        return count == 1
            ? FsBundle.get("area.affected-one")
            : FsBundle.format("area.affected", count);
    }

    /** Every analysed building is one FactoryScope has no production model for. */
    private boolean onlyLimitedSupport(){
        Integer limited = result.summary.byStatus.get(AreaStatus.limitedDiagnostics);
        return result.summary.analyzed > 0 && limited != null && limited == result.summary.analyzed;
    }

    /**
     * One collapsible issue group. The affected-building rows are built the first time the group is
     * expanded, and then a page at a time: a large selection can produce hundreds of them, and none are
     * worth constructing until somebody asks to see them.
     */
    private void issueRow(Table table, AreaIssueGroup group){
        Table listing = new Table();
        Table rows = new Table();
        Table footer = new Table();
        listing.add(rows).growX().row();
        listing.add(footer).growX().row();

        Collapser collapser = new Collapser(listing, true);
        collapser.setDuration(0.15f);
        boolean[] filled = {false};

        table.button(row -> {
            row.left().margin(4f);
            ContentIcons.add(row, kindOf(group), idOf(group), 22f);
            row.add(AreaText.issueTitle(group)).color(AreaText.color(group.severity))
                .growX().left().ellipsis(true).minWidth(0f);
            row.add(affected(group.buildingCount())).color(Pal.lightishGray).right().padLeft(8f);
        }, Styles.flatt, () -> {
            if(!filled[0]){
                fillBuildings(rows, footer, group, 0);
                filled[0] = true;
            }
            collapser.setCollapsed(!collapser.isCollapsed());
        }).growX().pad(2f).name("factoryscope-area-issue").row();

        table.add(collapser).growX().padLeft(12f).row();
    }

    private void fillBuildings(Table rows, Table footer, AreaIssueGroup group, int from){
        rows.left().defaults().growX().left();
        int to = Math.min(group.buildingCount(), from + PAGE);

        for(int i = from; i < to; i++){
            BuildingRef ref = group.buildings.get(i);
            rows.table(row -> {
                row.button(inner -> {
                    inner.left().margin(2f);
                    inner.add(ref.blockName).growX().left().ellipsis(true).minWidth(0f);
                    inner.add(FsBundle.format("area.coordinates", ref.tileX, ref.tileY))
                        .color(Pal.gray).right().padLeft(8f);
                }, Styles.flatt, () -> inspect(ref)).growX().name("factoryscope-area-building");

                row.button(Icon.zoomSmall, Styles.emptyi, () -> locate(ref)).size(34f).padLeft(4f)
                    .tooltip(FsBundle.ref("area.locate")).name("factoryscope-area-locate");
            }).growX().padBottom(2f).row();
        }

        footer.clear();
        if(to >= group.buildingCount()) return;

        //how much is hidden is stated, and getting the rest is one button rather than a smaller selection
        footer.add(FsBundle.format("area.listing-shown", to, group.buildingCount()))
            .color(Pal.lightOrange).left().padTop(4f);
        footer.button(FsBundle.ref("area.show-more"), Icon.downOpen, Styles.flatt,
                () -> fillBuildings(rows, footer, group, to))
            .height(36f).padLeft(8f).name("factoryscope-area-more");
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

    /**
     * Steps out of the way so the world can be seen. The report is kept, not discarded: the bar that
     * appears over the world is what brings it back.
     */
    private void locate(BuildingRef ref){
        if(AreaProbe.resolve(ref) == null){
            Vars.ui.showInfoToast(FsBundle.get("area.building-gone"), 2f);
            return;
        }
        hide();
        onLocate.get(ref);
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
