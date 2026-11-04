package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.host.HostActions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/** Native possession for normal players and explicit authoritative removal for the host. */
public final class UnitToolsDialog extends ToolDialog {
    private final HostActions actions;
    private String search = "";
    private int selected = -1;
    private boolean showActions;
    private final java.util.Set<Unit> chosen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final java.util.IdentityHashMap<Unit,Integer> chosenIds=new java.util.IdentityHashMap<>();
    private final java.util.Set<Integer> typeIds=new java.util.HashSet<>();
    private boolean allTeams,allTypes=true,showIndividuals;
    private int teamId,visibleCount=20;
    private Object world;
    private org.schemetools.host.UnitScope.Target selectedRef;
    private Runnable repaint=this::rebuild;
    private arc.scene.ui.layout.Table list, details;
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    public UnitToolsDialog(HostActions actions) {
        super(Core.bundle.get("st.unit-tools")); this.actions = actions;
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private Unit target() {
        Unit unit = Groups.unit.getByID(selected);
        if (unit == null || !unit.isAdded() || selectedRef==null || !selectedRef.current() || Vars.net.client() && unit.inFogTo(Vars.player.team()))
            throw new IllegalArgumentException("st.unit-tools-missing");
        return unit;
    }
    public static void control(Unit unit) {
        if (!Vars.state.isGame() || unit == null || !unit.isAdded() || unit.team != Vars.player.team() || unit.isPlayer()
                || !unit.type.playerControllable || !Vars.state.rules.possessionAllowed || unit.inFogTo(Vars.player.team()))
            throw new IllegalArgumentException("st.unit-tools-control-denied");
        Call.unitControl(Vars.player, unit);
        if (!Vars.net.client() && Vars.player.unit() != unit) throw new IllegalStateException("st.unit-tools-control-denied");
    }
    public static void controlBuilding(mindustry.gen.Building building) {
        if (!Vars.state.isGame() || Vars.player.dead() || building == null || !building.isValid()
                || building.team != Vars.player.team() || !Vars.state.rules.possessionAllowed
                || !(building instanceof mindustry.world.blocks.ControlBlock block) || !block.canControl())
            throw new IllegalArgumentException("st.unit-tools-control-denied");
        Call.unitControl(Vars.player, block.unit());
        if (!Vars.net.client() && Vars.player.unit() != block.unit()) throw new IllegalStateException("st.unit-tools-control-denied");
    }
    private void rebuild(){render(ToolUi.content(this),this::rebuild);}
    private org.schemetools.host.UnitScope scope(){return new org.schemetools.host.UnitScope(allTeams,teamId,allTypes,typeIds);}
    private boolean visible(Unit unit){return unit!=null&&unit.isAdded()&&!unit.dead&&(!Vars.net.client()||!unit.inFogTo(Vars.player.team()));}
    private boolean eligible(Unit unit){return visible(unit)&&!HostActions.protectedUnit(unit);}
    private boolean matches(Unit unit){return eligible(unit)&&(allTeams||unit.team.id==teamId)&&(allTypes||typeIds.contains((int)unit.type.id));}
    private void clearSelection(){chosen.clear();chosenIds.clear();selected=-1;selectedRef=null;showActions=false;}
    private void changeFilter(){clearSelection();visibleCount=20;repaint.run();}
    private void initWorld(){
        if(world==Vars.world.tiles)return;
        world=Vars.world.tiles;teamId=Vars.player==null?Vars.state.rules.defaultTeam.id:Vars.player.team().id;
        allTeams=false;allTypes=true;typeIds.clear();clearSelection();showIndividuals=false;search="";visibleCount=20;
    }
    private int signature(){int value=1;for(Unit unit:Groups.unit)if(visible(unit))value+=31*unit.id+unit.team.id*7+(int)unit.type.id+(HostActions.protectedUnit(unit)?1:0);return value;}
    void render(arc.scene.ui.layout.Table root,Runnable changed){
        repaint=changed;
        if(!Vars.state.isGame()){root.add(text("st.no-game")).wrap().row();return;}
        initWorld();chosen.removeIf(unit->!matches(unit)||!java.util.Objects.equals(chosenIds.get(unit),unit.id));
        var counts=new java.util.TreeMap<Integer,Integer>();int total=0;
        for(Unit unit:Groups.unit)if(eligible(unit)){counts.merge(unit.team.id,1,Integer::sum);total++;}
        counts.putIfAbsent(teamId,0);
        final int totalCount=total;
        root.table(header->{
            header.add(text("st.roster.team")).left().growX().minWidth(0);
            var all=header.button(text("st.roster.all-teams")+" · "+totalCount,ToolUi.toggleStyle(),()->{allTeams=true;changeFilter();}).width(ToolUi.width()*.48f).minHeight(36).get();all.margin(4);all.setChecked(allTeams);all.getLabel().setFontScale(.75f);all.getLabel().setWrap(true);
            ToolUi.helpButton(header,text("st.roster.team"),"st.roster.help");
        }).row();
        int columns=ToolUi.width()>=320?2:1;
        float card=(ToolUi.width()-4*(columns-1))/columns;
        root.table(teams->{int index=0;for(var entry:counts.entrySet()){
            var team=mindustry.game.Team.get(entry.getKey());
            var style=ToolUi.toggleStyle();style.fontColor=team.color;style.checkedFontColor=team.color;
            var button=teams.button(team.localized()+" · "+entry.getValue(),style,()->{allTeams=false;teamId=team.id;changeFilter();}).width(card).minHeight(40).padBottom(4).padRight(4).get();
            button.getLabel().setWrap(true);button.getLabel().setColor(team.color);button.setChecked(!allTeams&&teamId==team.id);button.name="st-roster-team-"+team.id;
            if(++index%columns==0)teams.row();
        }}).row();
        var groups=new java.util.TreeMap<Integer,Integer>();
        for(Unit unit:Groups.unit)if(eligible(unit)&&(allTeams||unit.team.id==teamId))groups.merge((int)unit.type.id,1,Integer::sum);
        root.table(header->{
            header.add(text("st.ui.type")).left().growX().minWidth(0);
            var types=header.button(text("st.roster.all-types"),ToolUi.toggleStyle(),()->{allTypes=true;typeIds.clear();changeFilter();}).width(ToolUi.width()*.38f).minHeight(36).get();types.margin(4);types.setChecked(allTypes);types.getLabel().setFontScale(.8f);types.getLabel().setWrap(true);
            ToolUi.helpButton(header,text("st.ui.type"),"st.roster.help");
        }).row();
        root.table(grid->{int index=0;for(var entry:groups.entrySet()){
            var type=Vars.content.unit(entry.getKey());
            boolean marked=!allTypes&&typeIds.contains((int)type.id);
            var button=grid.button(cell->{
                cell.image(type.uiIcon).size(34).padRight(5);
                cell.table(texts->{texts.add(type.localizedName).wrap().width(Math.max(55,card-84)).left().row();texts.add("×"+entry.getValue()).color(arc.graphics.Color.lightGray).left();});
                if(marked)cell.image(mindustry.gen.Icon.ok).size(16).color(mindustry.graphics.Pal.accent);
            },ToolUi.toggleStyle(),()->{
                if(allTypes){allTypes=false;typeIds.clear();typeIds.add((int)type.id);}else if(!typeIds.remove((int)type.id))typeIds.add((int)type.id);
                changeFilter();
            }).width(card).minHeight(68).padBottom(4).padRight(4).get();
            button.setChecked(!allTypes&&typeIds.contains((int)type.id));button.name="st-roster-type-"+type.name;
            button.addListener(new arc.scene.ui.Tooltip(t->t.add(type.localizedName)));if(++index%columns==0)grid.row();
        }}).row();
        if(groups.isEmpty())root.add(text("st.units-empty")).wrap().row();
        var matching=new ArrayList<Unit>();for(Unit unit:Groups.unit)if(matches(unit))matching.add(unit);
        String teamName=allTeams?text("st.roster.all-teams"):mindustry.game.Team.get(teamId).localized();
        String typeName=allTypes?text("st.roster.all-types"):typeIds.size()==1?Vars.content.unit(typeIds.iterator().next()).localizedName:Core.bundle.format("st.ui.type-count",typeIds.size());
        root.add(teamName+" · "+typeName+" · "+matching.size()).wrap().color(mindustry.graphics.Pal.accent).row();
        String killLabel=!allTypes&&typeIds.size()==1?Core.bundle.format("st.ui.kill-type",matching.size(),typeName):text("st.roster.kill-matching")+" ("+matching.size()+")";
        var kill=ToolUi.action(root,killLabel,"st.roster.help",()->confirmKill(matching));kill.name="st-roster-kill-matching";kill.setDisabled(!actions.isAvailable()||matching.isEmpty());ToolUi.emphasize(kill,true);
        ToolUi.action(root,text("st.units-select-area"),"st.roster.help",this::selectArea).setDisabled(!actions.isAvailable());
        if(!chosen.isEmpty()){
            var remove=ToolUi.action(root,text("st.units-kill-selected")+" ("+chosen.size()+")","st.roster.help",()->confirmKill(new ArrayList<>(chosen)));remove.setDisabled(!actions.isAvailable());ToolUi.emphasize(remove,true);
        }
        var individual=root.button(text("st.roster.individuals")+(showIndividuals?" ▴":" ▾"),()->{showIndividuals=!showIndividuals;repaint.run();}).minHeight(36).get();individual.getLabel().setWrap(true);root.row();
        if(showIndividuals){
            root.table(row->{
                row.field(search,value->{search=value;visibleCount=20;refill();}).width(ToolUi.width()-44).height(40).get().setMessageText(text("st.search"));
                row.button(mindustry.gen.Icon.refresh,mindustry.ui.Styles.cleari,()->repaint.run()).size(40).padLeft(4).tooltip(text("st.refresh"));
            }).row();
            details=new arc.scene.ui.layout.Table();details.defaults().width(ToolUi.width()).left().padBottom(6);root.add(details).row();
            list=new arc.scene.ui.layout.Table();list.defaults().width(ToolUi.width()).left().padBottom(4);root.add(list).row();refill();showDetails();
        }else{list=null;details=null;}
        root.add(text("st.ui.players-protected")).color(arc.graphics.Color.lightGray).wrap().row();
        int[] last={signature()};float[] checked={arc.util.Time.time};
        root.update(()->{if(arc.util.Time.time-checked[0]<45||Core.scene.hasDialog()||Core.scene.hasKeyboard())return;checked[0]=arc.util.Time.time;int now=signature();if(world!=Vars.world.tiles||now!=last[0])repaint.run();});
    }
    public void selectArea(){
        initWorld();var filter=scope();
        new AreaSelectionDialog(Math.max(Vars.world.width(),Vars.world.height()),area->{
            clearSelection();
            for(Unit unit:Groups.unit)if(eligible(unit)&&filter.matches(unit)&&unit.tileX()>=area.x1()&&unit.tileX()<=area.x2()&&unit.tileY()>=area.y1()&&unit.tileY()<=area.y2()){
                chosen.add(unit);chosenIds.put(unit,unit.id);
            }
            if(!chosen.isEmpty()){Unit unit=chosen.iterator().next();selected=unit.id;selectedRef=org.schemetools.host.UnitScope.Target.capture(unit);}
            repaint.run();
        }).show();
    }
    private String scopeDescription(org.schemetools.host.UnitScope filter){
        String teams=filter.allTeams()?text("st.roster.all-teams"):mindustry.game.Team.get(filter.teamId()).localized();
        String types=filter.allTypes()?text("st.roster.all-types"):filter.typeIds().stream().sorted().map(id->Vars.content.unit(id).localizedName).collect(java.util.stream.Collectors.joining(", "));
        return text("st.roster.team")+": "+teams+"\n"+text("st.roster.types")+": "+types;
    }
    private void confirmKill(java.util.List<Unit> units){run(()->{
        if(!actions.isAvailable())throw new IllegalStateException("st.error.authority");
        var filter=scope();var targets=new ArrayList<org.schemetools.host.UnitScope.Target>();
        for(Unit unit:units)if(matches(unit)&&(!chosen.contains(unit)||java.util.Objects.equals(chosenIds.get(unit),unit.id)))targets.add(org.schemetools.host.UnitScope.Target.capture(unit));
        if(targets.isEmpty()){Vars.ui.showInfoFade(text("st.units-empty"));return;}
        var request=new org.schemetools.host.UnitScope.Request(Vars.world.tiles,filter,targets);
        Runnable task=WorldGuard.capture(()->{int count=actions.killUnits(request);clearSelection();repaint.run();Vars.ui.showInfoFade(Core.bundle.format("st.units-killed",count));});
        BaseDialog dialog=new BaseDialog(text("st.units"));dialog.closeOnBack();
        float width=Math.min(440,Core.scene.getWidth()/UiSizing.scale()-90);
        dialog.cont.pane(body->body.add(scopeDescription(filter)+"\n\n"+Core.bundle.format("st.units-kill-confirm",targets.size())).wrap().width(width)).width(width+32*UiSizing.scale()).maxHeight(UiSizing.height()*.6f).scrollX(false);
        dialog.buttons.button(text("cancel"),dialog::hide).width(120).height(44);
        dialog.buttons.button(text("ok"),()->{dialog.hide();run(task);}).width(120).height(44);dialog.show();
    });}
    private void refill(){
        if(list==null)return;list.clearChildren();
        var units=new ArrayList<Unit>();
        for(Unit unit:Groups.unit)if(visible(unit)&&scope().matches(unit)&&(unit.type.localizedName+" "+unit.type.name+" "+unit.id).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))units.add(unit);
        units.sort(Comparator.comparingDouble(unit->unit.dst2(Vars.player)));
        for(int i=0;i<Math.min(visibleCount,units.size());i++){
            Unit unit=units.get(i);
            var button=list.button(cell->{cell.image(unit.type.uiIcon).size(28);cell.add((chosen.contains(unit)?"✓ ":"")+unit.type.localizedName).wrap().width(ToolUi.width()-84).padLeft(6);},mindustry.ui.Styles.grayt,()->{
                selected=unit.id;selectedRef=org.schemetools.host.UnitScope.Target.capture(unit);showActions=false;
                if(!HostActions.protectedUnit(unit)){if(chosen.remove(unit))chosenIds.remove(unit);else{chosen.add(unit);chosenIds.put(unit,unit.id);}}
                repaint.run();
            }).minHeight(40).get();button.setChecked(chosen.contains(unit));button.name="st-unit-"+unit.id;list.row();
        }
        if(units.size()>visibleCount)list.button(text("st.roster.more"),()->{visibleCount+=20;refill();}).height(40).row();
        if(units.isEmpty())list.add(text("st.unit-tools-missing")).wrap().row();
    }
    private void showDetails(){
        details.clear();if(selected<0)return;
        try{
            Unit unit=target();
            ToolUi.action(details,text("st.units-selected-actions")+" #"+unit.id,"st.unit-tools-help",()->{showActions=!showActions;repaint.run();});
            if(!showActions)return;
            ToolUi.action(details,text("st.unit-tools-control"),"st.unit-tools-help",()->run(()->{control(target());hide();Vars.ui.showInfoFade(text("st.unit-tools-requested"));})).setDisabled(unit.isPlayer()||unit.team!=Vars.player.team()||!Vars.state.rules.possessionAllowed);
            ToolUi.action(details,text("st.player-spectate"),"st.unit-tools-help",()->run(()->{Vars.control.input.spectate(target());hide();}));
            ToolUi.action(details,text("st.player-remove-unit"),"st.player-remove-unit-warning",()->run(()->{
                Unit current=target();Runnable task=WorldGuard.capture(()->{actions.despawn(current);clearSelection();repaint.run();});
                Vars.ui.showConfirm(text("st.unit-tools"),text("st.player-remove-unit-warning"),()->run(task));
            })).setDisabled(!actions.isAvailable()||HostActions.protectedUnit(unit));
        }catch(RuntimeException failure){details.add(text("st.unit-tools-missing")).wrap();}
    }
    private static void run(Runnable task){try{task.run();}catch(RuntimeException failure){Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage()==null?"":failure.getMessage(),text("st.error.input")));}}
}
