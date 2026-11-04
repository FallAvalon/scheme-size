package org.schemetools.client;

import arc.Core;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.game.SpawnGroup;
import mindustry.ui.dialogs.BaseDialog;

/** Read-only preview based on the 160.7 wave schedule; no world state is changed. */
public final class WaveForecastDialog extends ToolDialog {
    private String report = "";

    public WaveForecastDialog() {
        super(Core.bundle.get("st.wave.title"));
        addCloseButton();
        buttons.button(Core.bundle.get("st.wave.copy"), () -> {
            Core.app.setClipboardText(report);
            Vars.ui.showInfoFade(Core.bundle.get("st.wave.copied"));
        }).size(190f, 48f);
        shown(this::rebuild);
    }

    private void rebuild() {
        cont.clear();
        if (!Vars.state.isGame()) {
            report = Core.bundle.get("st.no-game");
        } else {
            report = forecast();
        }
        cont.pane(table -> table.add(report).wrap().left().width(UiSizing.width(560f))).width(UiSizing.width(600f)).maxHeight(420f);
    }

    public String report() { return report; }

    private String forecast() {
        int wave = Vars.state.wave;
        int waveIndex = Math.max(0, wave - 1);
        StringBuilder result = new StringBuilder(Core.bundle.format("st.wave.header", wave)).append('\n');
        long total = 0;
        double health = 0, shield = 0;
        boolean uncertain = false;
        int ground = Vars.spawner.countGroundSpawns();
        int flying = Vars.spawner.countFlyerSpawns();
        for (SpawnGroup group : Vars.state.rules.spawns) {
            if (group.type == null) continue;
            if (group.spacing <= 0 || !Float.isFinite(group.unitScaling) || group.unitScaling <= 0f) {
                uncertain = true;
                continue;
            }
            int perPoint = group.getSpawned(waveIndex);
            if (perPoint <= 0) continue;
            boolean boss = group.effect == StatusEffects.boss;
            if (Vars.state.isCampaign()) {
                float multiplier = Vars.state.getPlanet().campaignRules.difficulty.enemySpawnMultiplier;
                perPoint = Math.max(1, boss ? (int)(perPoint * multiplier) : Mathf.round(perPoint * multiplier));
            }
            int points=group.type.flying?flying:ground;
            if(group.spawn!=-1){
                points=Vars.spawner.getSpawns().count(tile->tile.pos()==group.spawn&&(group.type.flying||Vars.state.hasSpawns()));
                if(Vars.state.rules.wavesSpawnAtCores&&Vars.state.rules.attackMode&&Vars.state.teams.isActive(Vars.state.rules.waveTeam)
                        &&Vars.state.rules.waveTeam.cores().contains(core->core.pos()==group.spawn)){
                    if(group.type.flying)points++;
                    else if(!Vars.state.teams.playerCores().isEmpty()){
                        if(Vars.state.rules.waveTeam.cores().size==1)points+=ground-(Vars.state.hasSpawns()?Vars.spawner.getSpawns().size:0);
                        else{uncertain=true;result.append(Core.bundle.format("st.wave.specific",group.type.localizedName,perPoint)).append('\n');}
                    }
                }
            }
            if(group.type.segmentUnits>1)uncertain=true;
            long count = (long)perPoint * points;
            total += count;
            health += count * (double)group.type.health;
            shield += count * (double)group.getShield(waveIndex);
            result.append(Core.bundle.format(boss ? "st.wave.boss" : "st.wave.unit",
                group.type.localizedName, count)).append('\n');
        }
        result.append(Core.bundle.format(uncertain ? "st.wave.partial" : "st.wave.total", total,
            Math.round(health), Math.round(shield)));
        result.append('\n').append(Core.bundle.get("st.wave.note"));
        if (uncertain) result.append('\n').append(Core.bundle.get("st.wave.uncertain"));
        return result.toString();
    }
}
