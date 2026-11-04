package org.schemetools.client;

import arc.Core;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.event.BossRound;
import org.schemetools.event.BossRoundController;

/** Small event HUD. All world operations still enter HostPanel and its authoritative services. */
public final class CombatDock {
    private final HostPanel panel;private final BossRoundController rounds;private final ToolWorkspace workspace;
    private final Table root=new Table();
    private boolean expanded=Core.settings.getBool("schemesize-combat-expanded",true),lastCompact;
    private int lastMask=-1;private float lastWidth=-1,lastHeight=-1;private boolean lastShown;
    private static final String[] ids={"pause","message","spawn","players","items","event","finish"};
    public CombatDock(HostPanel panel,BossRoundController rounds,ToolWorkspace workspace){
        this.panel=panel;this.rounds=rounds;this.workspace=workspace;root.setFillParent(true);root.top().left();root.touchable=Touchable.childrenOnly;root.name="st-combat-dock";
        arc.Events.run(mindustry.game.EventType.Trigger.update,()->{
            boolean show=Vars.state.isGame()&&rounds.isActive()&&Core.settings.getBool("schemesize-combat-hud",true)&&Core.settings.getBool("schemesize-module-event",true)&&Core.settings.getBool("schemetools-hud",true);
            root.visible=show;workspace.battleNavigation(show);
            boolean compact=!expanded||workspace.current()!=null;int mask=mask();float width=Core.scene.getWidth(),height=Core.scene.getHeight();
            if(show&&(!lastShown||compact!=lastCompact||mask!=lastMask||width!=lastWidth||height!=lastHeight)){lastCompact=compact;lastMask=mask;lastWidth=width;lastHeight=height;rebuild();}
            lastShown=show;
        });Vars.ui.hudGroup.addChild(root);rebuild();
    }
    private static boolean enabled(String id){return Core.settings.getBool("schemesize-combat-action-"+id,!id.equals("items")&&!id.equals("event")&&!id.equals("finish"));}
    private int mask(){int mask=0;for(int i=0;i<ids.length;i++)if(enabled(ids[i]))mask|=1<<i;return mask;}
    private String text(String key){return Core.bundle.get(key);}
    private String clock(){int seconds=Math.max(0,(int)Math.ceil(rounds.remaining()));return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private void rebuild(){
        root.clearChildren();float width=Math.min(280,Core.scene.getWidth()/UiSizing.scale()-24);
        root.marginTop(Math.min(Core.settings.getInt("schemesize-hud-top",96),Math.max(0,UiSizing.height()-180))).marginLeft(8);
        root.table(Tex.paneSolid,box->{box.margin(6);box.top();
            box.table(header->{
                var menu=header.button(Icon.menu,Styles.clearNonei,()->panel.openPage("overview")).size(30).padRight(4).tooltip(text("st.workspace.more")).get();menu.name="st-combat-tools";menu.getImageCell().size(18);
                var phase=header.label(()->text("st.boss.phase."+rounds.phase().name().toLowerCase(java.util.Locale.ROOT))).growX().minWidth(0).get();phase.setEllipsis(true);phase.setFontScale(.85f);header.label(this::clock).width(54).padLeft(4).get().setFontScale(.85f);
                var settings=header.button(Icon.settings,Styles.clearNonei,this::configure).size(30).get();settings.name="st-combat-settings";settings.getImageCell().size(18);
                header.button(expanded?"−":"+",Styles.cleart,()->{expanded=!expanded;Core.settings.put("schemesize-combat-expanded",expanded);lastMask=-1;}).size(26,30).get().name="st-combat-collapse";
            }).growX().row();
            box.label(()->rounds.bossName()==null?"":rounds.bossName()).growX().minWidth(0).get().setEllipsis(true);box.row();
            if(!expanded||workspace.current()!=null)return;
            box.table(actions->{int index=0;for(String id:ids)if(enabled(id)){
                var button=actions.button(text("st.combat."+id),()->execute(id)).width((width-16)/2).minHeight(36).pad(2).get();button.getLabel().setEllipsis(true);button.getLabel().setFontScale(.85f);button.name="st-combat-"+id;
                button.update(()->{button.setDisabled(!panel.canOrganize()||id.equals("pause")&&rounds.phase()==BossRound.Phase.PAUSED&&rounds.bossDisconnected());if(id.equals("pause"))button.setText(text(rounds.phase()==BossRound.Phase.PAUSED?"st.combat.resume-short":"st.combat.pause-short"));if(id.equals("spawn"))button.setText(panel.combatSpawnLabel());});
                if(++index%2==0)actions.row();
            }}).growX();
        }).width(width).top();
    }
    private void execute(String id){switch(id){
        case "pause"->panel.combatPause();case "message"->panel.combatMessage();case "spawn"->panel.combatSpawn();case "players"->panel.openPage("players");case "items"->panel.openPage("items");case "event"->panel.openPage("event");case "finish"->panel.combatFinish();default->throw new IllegalArgumentException(id);
    }}
    private void configure(){
        BaseDialog dialog=new BaseDialog(text("st.combat.settings"));dialog.name="st-modal";dialog.addCloseButton();
        float width=Math.min(300,Core.scene.getWidth()/UiSizing.scale()-80);
        dialog.cont.pane(body->{
            for(String id:ids)body.check(text("st.combat."+id),enabled(id),v->{Core.settings.put("schemesize-combat-action-"+id,v);lastMask=-1;}).width(width-24).left().minHeight(36).row();
            body.add(text("st.combat.message")).left().row();body.field(Core.settings.getString("schemesize-combat-message",""),s->Core.settings.put("schemesize-combat-message",s)).width(width-24).maxTextLength(200).row();
            body.button(text("st.combat.configure-spawn"),()->{dialog.hide();panel.combatSpawnSettings();}).width(width-24).height(40).row();
        }).width(width).maxHeight(UiSizing.height()*.65f).scrollX(false);dialog.show();
    }
}