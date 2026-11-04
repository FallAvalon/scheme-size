package org.schemetools.client;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.Element;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.ui.FileChooser.FileChooserParams;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.host.ArenaTools;
import org.schemetools.schematic.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Common-edition library with explicit copy/import/export and vanilla building placement. */
public final class SchematicsDialog extends ToolDialog {
    private final SchematicStore store;
    private final ArenaTools arena;
    private final List<Schematic> library = new ArrayList<>();
    private Schematic selected;
    private Schematic sectionParent;
    private String sectionX = "0", sectionY = "0", sectionWidth = "128", sectionHeight = "128";
    private SchematicTools.Layer layer = SchematicTools.Layer.BUILDINGS;
    private String search = "", tag = "", name = "", labels = "", x1 = "0", y1 = "0", x2 = "4", y2 = "4", pasteX = "0", pasteY = "0";
    private String warning = "";
    private Table list;
    private boolean advanced, imports, captureTab;
    private int visibleCount=12;
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}

    public SchematicsDialog(SchematicStore store, ArenaTools arena) {
        super(text("st.schematic.title"));
        this.store = store; this.arena = arena;
        addCloseButton();
        shown(() -> { refresh(); rebuild(); });
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void refresh() {
        library.clear();
        library.addAll(java.util.stream.StreamSupport.stream(Vars.schematics.all().spliterator(), false).toList());
        try {
            var listing = store.list();
            for (var entry : listing.entries()) library.add(entry.schematic());
            warning = listing.unreadable().isEmpty() ? "" : Core.bundle.format("st.schematic.unreadable", listing.unreadable().size());
        } catch (IOException failure) { warning = text("st.schematic.error.file"); }
    }
    private void rebuild(){
        Table root=ToolUi.content(this);
        ToolUi.tabs(root,text("st.ui.library"),text("st.ui.from-map"),captureTab,()->{captureTab=false;rebuild();},()->{captureTab=true;rebuild();});
        if(!captureTab){
            ToolUi.field(root,text("st.search"),search,value->{search=value;visibleCount=12;refillList();});
            list=new Table();root.pane(list).width(ToolUi.width()).maxHeight(Math.min(260,UiSizing.toolHeight(.4f))).scrollX(false).row();refillList();
        }else list=null;
        if(selected!=null){
            String title=selected.tags.get("name",text("st.schematic.unnamed"));
            root.add(title+" · "+selected.width+" × "+selected.height).wrap().color(mindustry.graphics.Pal.accent).row();
            details(root);
        }
        ToolUi.action(root,text("st.selection.title"),"st.help.capture",this::captureOnMap).setDisabled(!Vars.state.isGame());
        if(selected!=null){
            ToolUi.action(root,text("st.schematic.save-copy"),"st.help.save",()->{
                Vars.ui.showTextInput(text("st.schematic.name"),text("st.schematic.name"),80,name,false,value->run(()->{name=value;metadata();store.saveCopy(selected);refresh();captureTab=false;rebuild();Vars.ui.showInfoFade(text("st.schematic.saved"));}));
            });
        }
        if(captureTab||advanced)
        ToolUi.action(root,text("st.short.layer")+": "+text("st.schematic.layer."+layer.name().toLowerCase(Locale.ROOT)),"st.help.layer",()->{
            BaseDialog picker=new BaseDialog(text("st.short.layer"));picker.name="st-modal";picker.addCloseButton();
            for(var value:SchematicTools.Layer.values())picker.cont.button(text("st.schematic.layer."+value.name().toLowerCase(Locale.ROOT)),()->{layer=value;picker.hide();rebuild();}).width(240).height(44).row();picker.show();
        });
        ToolUi.action(root,text("st.short.import"),"st.help.import",()->{imports=!imports;rebuild();});
        if(imports){
            ToolUi.action(root,text("st.schematic.import-file"),"st.help.import",()->new FileChooserParams().open(true).extensions("msch","mtls").submit(file->run(()->select(SchematicStore.readFile(file.file().toPath())))));
            ToolUi.action(root,text("st.schematic.import-base64"),"st.help.clipboard",()->run(()->select(SchematicCodec.readBase64(Core.app.getClipboardText()))));
            ToolUi.action(root,text("st.image.title"),"st.help.image",()->new ImageImportDialog(value->{select(value);show();}).show());
        }
        ToolUi.action(root,text("st.workspace.advanced")+(advanced?" ▴":" ▾"),"st.schematic.help",()->{advanced=!advanced;rebuild();});
        if(advanced){
            ToolUi.field(root,text("st.schematic.tag"),tag,value->{tag=value;refillList();});
            ToolUi.action(root,text("st.refresh"),"st.help.refresh",()->{refresh();rebuild();});
            String[] values={x1,y1,x2,y2},names={"X1","Y1","X2","Y2"};
            for(int i=0;i<4;i++){int index=i;ToolUi.valueField(root,names[i],values[i],value->{switch(index){case 0->x1=value;case 1->y1=value;case 2->x2=value;default->y2=value;}});}
            ToolUi.action(root,text("st.schematic.at-player"),"st.help.near",()->{int x=Vars.player.tileX(),y=Vars.player.tileY();x1=""+Math.max(0,x-2);y1=""+Math.max(0,y-2);x2=""+Math.min(Vars.world.width()-1,x+2);y2=""+Math.min(Vars.world.height()-1,y+2);rebuild();}).setDisabled(!Vars.state.isGame());
            ToolUi.action(root,text("st.schematic.capture"),"st.help.capture",()->run(()->select(SchematicTools.capture(integer(x1),integer(y1),integer(x2),integer(y2),layer)))).setDisabled(!Vars.state.isGame());
        }
        if(!warning.isEmpty())root.add(warning).wrap().row();
    }
    private void captureOnMap() {
        new AreaSelectionDialog(512, area -> {
            x1=""+area.x1(); y1=""+area.y1(); x2=""+area.x2();y2=""+area.y2();
            run(() -> select(SchematicTools.capture(area.x1(),area.y1(),area.x2(),area.y2(),layer)));
        }).show();
    }
    private void refillList() {
        if (list == null) return;
        list.clearChildren();
        String query = search.toLowerCase(Locale.ROOT), filter = tag.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (Schematic schematic : library) {
            String title = schematic.tags.get("name", text("st.schematic.unnamed"));
            if (!Strings.stripColors(title).toLowerCase(Locale.ROOT).contains(query)
                    || !filter.isEmpty() && !schematic.labels.contains(value -> value.toLowerCase(Locale.ROOT).contains(filter))) continue;
            matches++;
            if(matches>visibleCount)continue;
            int columns=ToolUi.width()>=320?2:1;float card=(ToolUi.width()-24-6*(columns-1))/columns;
            var button=list.button(cell->{
                cell.add(new Preview(schematic)).size(card-16,68).row();
                var label=cell.add(title).width(card-16).get();label.setEllipsis(true);label.setFontScale(.85f);cell.row();
                cell.add(schematic.width+" × "+schematic.height).color(arc.graphics.Color.lightGray).get().setFontScale(.7f);
            },ToolUi.toggleStyle(),()->select(schematic)).width(card).minHeight(112).pad(3).get();
            button.setChecked(schematic==selected);button.name="st-schematic-card-"+(matches-1);button.addListener(new arc.scene.ui.Tooltip(t->t.add(title)));
            if(matches%columns==0)list.row();
        }
        list.row();
        if(matches>visibleCount)list.button(text("st.ui.more-schematics"),()->{visibleCount+=12;refillList();}).width(ToolUi.width()-24).height(40).colspan(ToolUi.width()>=320?2:1).row();
        if (matches == 0) list.add(text(library.isEmpty()?"st.ui.empty-library":"st.schematic.no-matches")).wrap().width(ToolUi.width()-24).row();
    }
    private void select(Schematic value) {
        sectionParent = null;
        selected = value; name = value.tags.get("name", ""); labels = String.join(", ", value.labels.toArray(String.class)); rebuild();
    }
    private void details(Table root){
        if(!SchematicTools.terrain(selected)){
        var use=ToolUi.action(root,text("st.schematic.use"),"st.help.paste",()->run(()->{
            Schematic value=selected;
            if(value.tiles.size>SchematicTools.MAX_PLACEMENT_PLANS)Vars.ui.showConfirm(text("st.schematic.title"),text("st.schematic.dense-warning"),()->run(()->{SchematicTools.use(value);hide();}));
            else{SchematicTools.use(value);hide();}
        }));use.setDisabled(!Vars.state.isGame());ToolUi.emphasize(use,false);
        }else{
            ToolUi.action(root,text("st.workspace.point"),"st.help.terrain",()->new AreaSelectionDialog(1,p->{pasteX=""+p.x1();pasteY=""+p.y1();pasteTerrain();}).show()).setDisabled(Vars.net.client()||!Vars.state.isGame());
            ToolUi.action(root,text("st.arena.undo"),"st.help.undo",()->run(()->arena.undo())).setDisabled(Vars.net.client()||!Vars.state.isGame());
            if(TerrainPaste.active()){
                root.label(TerrainPaste::status).wrap().row();
                ToolUi.action(root,text("st.schematic.batch-cancel"),"st.help.cancel-batch",TerrainPaste::cancel);
            }
        }
        if(!advanced)return;
        root.add(new Preview(selected)).size(Math.min(260,ToolUi.width()),Math.min(160,ToolUi.width()*.6f)).row();
        ToolUi.valueField(root,text("st.schematic.name"),name,v->name=v);
        ToolUi.valueField(root,text("st.schematic.tags"),labels,v->labels=v);
        ToolUi.action(root,text("st.schematic.export-base64"),"st.help.export",()->run(()->{metadata();Core.app.setClipboardText(SchematicCodec.writeBase64(selected));}));
        ToolUi.action(root,text("st.schematic.export-file"),"st.help.export",()->{
            String extension=SchematicStore.extension(selected);new FileChooserParams().open(false).extensions(extension).name("schematic."+extension).submit(file->run(()->{metadata();SchematicStore.exportNew(selected,file.file().toPath());}));
        });
        if(selected.width>128||selected.height>128||selected.tiles.size>SchematicTools.MAX_PLACEMENT_PLANS){
            ToolUi.valueField(root,"X",sectionX,v->sectionX=v);ToolUi.valueField(root,"Y",sectionY,v->sectionY=v);
            ToolUi.valueField(root,"W",sectionWidth,v->sectionWidth=v);ToolUi.valueField(root,"H",sectionHeight,v->sectionHeight=v);
            ToolUi.action(root,text("st.schematic.section"),"st.schematic.section-help",()->run(()->{Schematic origin=selected;select(SchematicTools.section(origin,integer(sectionX),integer(sectionY),integer(sectionWidth),integer(sectionHeight)));sectionParent=origin;rebuild();}));
        }
        if(sectionParent!=null)ToolUi.action(root,text("st.schematic.section-back"),"st.schematic.section-help",()->select(sectionParent));
        if(SchematicTools.terrain(selected)){
            ToolUi.valueField(root,"X",pasteX,v->pasteX=v);ToolUi.valueField(root,"Y",pasteY,v->pasteY=v);
            ToolUi.action(root,text("st.schematic.terrain-apply"),"st.help.terrain",this::pasteTerrain).setDisabled(Vars.net.client()||!Vars.state.isGame());
        }
    }
    private void pasteTerrain(){run(()->{
        var previews=SchematicTools.terrainPreviews(arena,selected,integer(pasteX),integer(pasteY));
        int count=previews.stream().mapToInt(ArenaTools.Edit::count).sum();
        Vars.ui.showConfirm(text("st.schematic.title"),Core.bundle.format("st.schematic.batch-confirm",count,previews.size()),()->run(()->{TerrainPaste.start(arena,previews);rebuild();}));
    });}
    private void metadata() throws IOException {
        String cleanName = name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 80 || cleanName.chars().anyMatch(Character::isISOControl)) throw new IOException("st.schematic.error.tags");
        String[] tags = labels.isBlank() ? new String[0] : labels.split(",", -1);
        if (tags.length > 64) throw new IOException("st.schematic.error.tags");
        for (String tag : tags) if (tag.trim().isEmpty() || tag.trim().length() > 64 || tag.chars().anyMatch(Character::isISOControl)) throw new IOException("st.schematic.error.tags");
        // Clone metadata before editing so a vanilla-library entry is never changed implicitly.
        arc.struct.StringMap metadata = new arc.struct.StringMap();
        for (var value : selected.tags) metadata.put(value.key, value.value);
        Schematic copy = new Schematic(selected.tiles.copy(), metadata, selected.width, selected.height);
        copy.tags.put("name", cleanName);
        for (String tag : tags) copy.labels.add(tag.trim());
        selected = copy;
    }
    private static int integer(String value) { return Integer.parseInt(value.trim()); }
    private static void run(IoAction action) {
        try { action.run(); }
        catch (IOException | RuntimeException failure) {
            String key = failure.getMessage();
            Vars.ui.showErrorMessage(key != null && Core.bundle.has(key) ? text(key) : text("st.schematic.error.data"));
        }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static final class Preview extends Element {
        private final Schematic schematic;
        Preview(Schematic schematic) { this.schematic = schematic; }
        @Override public void draw() {
            Draw.flush();if(!clipBegin())return;
            try{
            Draw.color(0.12f, 0.12f, 0.12f, parentAlpha); Fill.rect(x + width / 2, y + height / 2, width, height);
            float cell = Math.min(width / schematic.width, height / schematic.height);
            float left = x + (width - cell * schematic.width) / 2, bottom = y + (height - cell * schematic.height) / 2;
            int stride = Math.max(1, (schematic.tiles.size + 8191) / 8192);
            for (int i = 0; i < schematic.tiles.size; i += stride) {
                var tile = schematic.tiles.get(i);
                float size = tile.block instanceof mindustry.world.blocks.environment.Floor ? 1 : tile.block.size;
                float cx=left+(tile.x+.5f)*cell,cy=bottom+(tile.y+.5f)*cell;
                if(stride==1&&!(tile.block instanceof mindustry.world.blocks.environment.Floor)){
                    Draw.color(arc.graphics.Color.white,parentAlpha);Draw.rect(tile.block.fullIcon,cx,cy,size*cell,size*cell,tile.block.rotate?tile.rotation*90:0);
                }else{
                    Draw.color(tile.block.mapColor,parentAlpha);Fill.square(cx,cy,Math.max(.5f,size*cell/2));
                }
            }
            }finally{Draw.reset();Draw.flush();clipEnd();}
        }
    }
}
