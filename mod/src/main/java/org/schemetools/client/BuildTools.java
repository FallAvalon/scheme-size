package org.schemetools.client;

import arc.math.geom.Rect;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;
import org.schemetools.host.ArenaTools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ordinary local build queue and vanilla validated requests; never a remote admin backend. */
public final class BuildTools {
    public static final int MAX_PLANS = 1024;
    private List<BuildPlan> lastDeleted = List.of();
    private Object deletedWorld;
    private long lastRequest;
    private final List<BuildPlan> replacements = new ArrayList<>();
    private Unit replacementUnit;
    private Object replacementWorld;
    private boolean installed;
    private String replacementState = "idle";
    private PendingDrop pendingDrop;
    private String dropStatus = "st.build.priority-drop-idle";
    public String replacementState() { return replacementState; }
    public void install() {
        if (installed || Vars.headless) return; installed = true;
        arc.Events.run(mindustry.game.EventType.Trigger.update, () -> { updateReplacement(); updatePreferredDrop(); });
        arc.Events.on(mindustry.game.EventType.ResetEvent.class, event -> { cancelReplacement(); cancelPreferredDrop(); });
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> { cancelReplacement(); cancelPreferredDrop(); });
    }
    public int pendingReplacements() { return replacements.size(); }
    public void cancelReplacement() { replacements.clear(); replacementUnit = null; replacementWorld = null; replacementState = "cancelled"; }

    public static Unit builder() {
        Unit unit = localUnit();
        if (!unit.canBuild())
            throw new IllegalStateException("st.build.error.builder");
        return unit;
    }
    private static Unit localUnit() {
        if (Vars.headless || !Vars.state.isGame() || Vars.player.dead()) throw new IllegalStateException("st.build.error.builder");
        return Vars.player.unit();
    }

    public List<BuildPlan> fill(ArenaTools.Area area, Block block, int rotation) {
        return fill(area, block, rotation, false);
    }
    public List<BuildPlan> fill(ArenaTools.Area area, Block block, int rotation, boolean outline) {
        builder(); content(block);
        if (area.x2() >= Vars.world.width() || area.y2() >= Vars.world.height()) throw new IllegalArgumentException("st.arena.error.area");
        List<BuildPlan> plans = new ArrayList<>();
        int low = -(block.size - 1) / 2, high = block.size / 2;
        for (int y = area.y1() - low; y + high <= area.y2(); y += block.size)
            for (int x = area.x1() - low; x + high <= area.x2(); x += block.size) {
                if (!area.contains(x, y)) continue;
                if (outline) {
                    if (area.shape() == ArenaTools.Shape.RECTANGLE) {
                        if (x + low - area.x1() >= block.size && area.x2() - (x + high) >= block.size
                                && y + low - area.y1() >= block.size && area.y2() - (y + high) >= block.size) continue;
                    } else {
                        double rx = (area.x2() - area.x1() + 1) / 2d, ry = (area.y2() - area.y1() + 1) / 2d;
                        double ix = Math.max(0, rx - block.size), iy = Math.max(0, ry - block.size);
                        if (ix > 0 && iy > 0 && Math.pow((x - (area.x1() + area.x2()) / 2d) / ix, 2)
                                + Math.pow((y - (area.y1() + area.y2()) / 2d) / iy, 2) < 1) continue;
                    }
                }
                if (!Build.validPlace(block, Vars.player.team(), x, y, rotation)) throw new IllegalStateException("st.build.error.placement");
                plans.add(new BuildPlan(x, y, rotation, block));
            }
        checkCount(plans.size());
        return plans;
    }

    /** Replaces incompatible same-size blocks through ordinary deconstruction followed by construction. */
    public int replaceCluster(int x, int y, Block replacement) {
        Unit unit = builder(); content(replacement);
        if (!replacements.isEmpty()) throw new IllegalStateException("st.build.error.replacement-active");
        Building seed = Vars.world.build(x, y);
        if (seed == null || seed.team != unit.team || seed.block == replacement || seed.block.size != replacement.size)
            throw new IllegalArgumentException("st.build.error.size");
        var buildings = connected(seed);
        List<BuildPlan> breaks = new ArrayList<>(), builds = new ArrayList<>();
        for (Building building : buildings) {
            if (!Build.validBreak(unit.team, building.tileX(), building.tileY())
                    || Build.getEnemyOverlap(replacement, unit.team, building.tileX(), building.tileY()) != null
                    || !replacement.canPlaceOn(building.tile, unit.team, building.rotation))
                throw new IllegalStateException("st.build.error.placement");
            breaks.add(new BuildPlan(building.tileX(), building.tileY()));
            builds.add(new BuildPlan(building.tileX(), building.tileY(), building.rotation, replacement));
        }
        enqueue(breaks); replacements.addAll(builds); replacementUnit = unit; replacementWorld = Vars.world.tiles;
        replacementState = "deconstructing";
        unit.updateBuilding = true;
        return builds.size();
    }
    private void updateReplacement() {
        if (replacements.isEmpty()) return;
        if (!Vars.state.isGame() || Vars.world.tiles != replacementWorld || Vars.player.unit() != replacementUnit || Vars.player.dead()) {
            cancelReplacement(); return;
        }
        for (var iterator = replacements.iterator(); iterator.hasNext();) {
            BuildPlan plan = iterator.next(); var tile = Vars.world.tile(plan.x, plan.y);
            if (tile == null) { replacementState = "missing-tile"; iterator.remove(); continue; }
            boolean queued = hasPlan(replacementUnit, plan.x, plan.y, true);
            if (tile.block() != mindustry.content.Blocks.air) {
                if (!queued && !(tile.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild)) { replacementState = "queue-cancelled"; iterator.remove(); }
                continue;
            }
            if (Build.validPlace(plan.block, replacementUnit.team, plan.x, plan.y, plan.rotation)
                    && !hasPlan(replacementUnit, plan.x, plan.y, false)) {
                replacementUnit.removeBuild(plan.x, plan.y, true); replacementUnit.addBuild(plan); replacementState = "build-queued"; iterator.remove();
            } else {
                replacementState = "placement-refused"; iterator.remove();
                arc.Core.app.post(() -> Vars.ui.showInfoFade(arc.Core.bundle.format("st.build.replace-refused", plan.x, plan.y)));
            }
        }
    }
    private static boolean hasPlan(Unit unit, int x, int y, boolean breaking) {
        for (BuildPlan plan : unit.plans) if (plan.x == x && plan.y == y && plan.breaking == breaking) return true;
        return false;
    }

    public List<BuildPlan> cluster(int x, int y, Block replacement, boolean breaking) {
        builder();
        Building seed = Vars.world.build(x, y);
        if (seed == null || seed.team != Vars.player.team()) throw new IllegalArgumentException("st.build.error.target");
        if (!breaking) {
            content(replacement);
            if (replacement.size != seed.block.size) throw new IllegalArgumentException("st.build.error.size");
        }
        List<Building> buildings = connected(seed);
        List<BuildPlan> plans = new ArrayList<>();
        for (Building building : buildings) {
            if (breaking) {
                if (!Build.validBreak(Vars.player.team(), building.tileX(), building.tileY())) throw new IllegalStateException("st.build.error.placement");
                plans.add(new BuildPlan(building.tileX(), building.tileY()));
            } else {
                if (!Build.validPlace(replacement, Vars.player.team(), building.tileX(), building.tileY(), building.rotation))
                    throw new IllegalStateException("st.build.error.placement");
                plans.add(new BuildPlan(building.tileX(), building.tileY(), building.rotation, replacement));
            }
        }
        return plans;
    }

    private static List<Building> connected(Building seed) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Building> pending = new ArrayDeque<>();
        List<Building> result = new ArrayList<>();
        pending.add(seed); seen.add(seed.pos());
        while (!pending.isEmpty()) {
            Building building = pending.removeFirst();
            result.add(building);
            if (result.size() > MAX_PLANS) throw new IllegalStateException("st.build.error.count");
            for (Building near : building.proximity) if (near.team == seed.team && near.block == seed.block && seen.add(near.pos())) pending.add(near);
        }
        return result;
    }

    /** Prevalidates the whole batch and refuses overlap with the user's existing queue. */
    public int enqueue(List<BuildPlan> plans) {
        Unit unit = builder(); checkCount(plans.size());
        if (unit.plans.size + plans.size() > MAX_PLANS * 4) throw new IllegalStateException("st.build.error.count");
        List<BuildPlan> saved = new ArrayList<>();
        Rect a = new Rect(), b = new Rect();
        for (BuildPlan plan : plans) {
            Tile tile = Vars.world.tile(plan.x, plan.y);
            if (tile == null) throw new IllegalArgumentException("st.arena.error.area");
            if (plan.breaking) {
                if (!Build.validBreak(unit.team, plan.x, plan.y) || tile.build == null) throw new IllegalStateException("st.build.error.placement");
                saved.add(new BuildPlan(plan.x, plan.y, tile.build.rotation, tile.block(), tile.build.config()));
            } else {
                content(plan.block);
                if (!Build.validPlace(plan.block, unit.team, plan.x, plan.y, plan.rotation)) throw new IllegalStateException("st.build.error.placement");
            }
            for (BuildPlan existing : unit.plans) if (plan.bounds(a).overlaps(existing.bounds(b)))
                throw new IllegalStateException("st.build.error.queue-conflict");
        }
        for (BuildPlan plan : plans) unit.addBuild(plan);
        if (!saved.isEmpty()) { lastDeleted = List.copyOf(saved); deletedWorld = Vars.world.tiles; }
        return plans.size();
    }

    /** Restores construction plans, never inventories or already spent construction resources. */
    public int restoreDeleted() {
        Unit unit = builder();
        if (deletedWorld != Vars.world.tiles || lastDeleted.isEmpty()) throw new IllegalStateException("st.build.error.no-restore");
        List<BuildPlan> restore = new ArrayList<>();
        for (BuildPlan plan : lastDeleted) {
            Tile tile = Vars.world.tile(plan.x, plan.y);
            if (tile == null) throw new IllegalStateException("st.build.error.placement");
            if (tile.block() != plan.block) {
                if (!Build.validPlace(plan.block, unit.team, plan.x, plan.y, plan.rotation)) throw new IllegalStateException("st.build.error.placement");
                restore.add(plan.copy());
            }
        }
        List<BuildPlan> previous = new ArrayList<>(); for (BuildPlan plan : unit.plans) previous.add(plan.copy());
        int count;
        try {
            for (BuildPlan original : lastDeleted) unit.removeBuild(original.x, original.y, true);
            count = restore.isEmpty() ? lastDeleted.size() : enqueue(restore);
        } catch (RuntimeException failure) {
            unit.plans.clear(); for (BuildPlan plan : previous) unit.addBuild(plan); throw failure;
        }
        lastDeleted = List.of();
        return count;
    }

    public int connectPower(int x, int y) {
        localUnit(); rate();
        Building selected = Vars.world.build(x, y);
        if (selected == null || selected.team != Vars.player.team() || !(selected.block instanceof PowerNode node))
            throw new IllegalArgumentException("st.build.error.power");
        List<Building> candidates = new ArrayList<>();
        // The update group omits non-updating blocks such as power nodes. Use the building index.
        Vars.indexer.eachBlock(selected, node.laserRange * Vars.tilesize + Vars.tilesize,
            b -> b != selected && b.power != null && !selected.power.links.contains(b.pos())
                && node.linkValid(selected, b, true) && !PowerNode.insulated(selected, b), candidates::add);
        candidates.sort(java.util.Comparator.comparingDouble(selected::dst2));
        int count = Math.min(Math.max(0, node.maxNodes - selected.power.links.size), candidates.size());
        for (int i = 0; i < count; i++) Call.tileConfig(Vars.player, selected, candidates.get(i).pos());
        return count;
    }

    /** A preview keeps the world and target identity so a later confirmation cannot use stale plans. */
    public static final class PowerLine {
        private final Object world;
        private final Building target;
        private final Building start;
        private final PowerNode node;
        private final List<BuildPlan> plans;
        private PowerLine(Building target, Building start, PowerNode node, List<BuildPlan> plans) {
            world=Vars.world.tiles;this.target=target;this.start=start;this.node=node;this.plans=List.copyOf(plans);
        }
        public int count(){return plans.size();}
        public int targetX(){return target.tileX();}
        public int targetY(){return target.tileY();}
        public String targetName(){return target.block.localizedName;}
    }

    /** Native node placement path toward the nearest allied power building outside the selected radius. */
    public PowerLine powerLine(int x,int y,int radius,Block selected){
        Unit unit=builder();content(selected);
        if(!(selected instanceof PowerNode node)||!node.autolink||node.maxNodes<2||radius<0||radius>512||Vars.world.tile(x,y)==null)
            throw new IllegalArgumentException("st.build.error.power-line");
        final Building[] nearest={null};final double[] distance={Double.POSITIVE_INFINITY};
        float search=(float)Math.hypot(Vars.world.width(),Vars.world.height())*Vars.tilesize;
        final long deadline=System.nanoTime()+50_000_000L;final int[] visited={0};
        Vars.indexer.eachBlock(unit.team,x*Vars.tilesize,y*Vars.tilesize,search,building -> {
            if(++visited[0]>50_000||((visited[0]&127)==0&&System.nanoTime()>deadline))
                throw new IllegalStateException("st.build.error.power-search-limit");
            if(building==null||!building.isValid()||building.team!=unit.team||building.power==null||!building.block.connectedPower
                    ||node.sameBlockConnection&&building.block!=node
                    ||Math.abs(building.tileX()-x)<=radius&&Math.abs(building.tileY()-y)<=radius)return false;
            if(building.block instanceof PowerNode other&&building.power.links.size>=other.maxNodes)return false;
            return building.block.outputsPower||building.block.consumesPower||building.block instanceof PowerNode;
        },building -> {
            double dx=(double)building.tileX()-x,dy=(double)building.tileY()-y,d=dx*dx+dy*dy;
            if(d<distance[0]){nearest[0]=building;distance[0]=d;}
        });
        Building target=nearest[0];if(target==null)throw new IllegalArgumentException("st.build.error.power-target");
        var points=mindustry.input.Placement.pathfindLine(false,x,y,target.tileX(),target.tileY());
        node.changePlacementPath(points,0);
        if(points.size<2||points.size>MAX_PLANS+2)throw new IllegalStateException("st.build.error.count");
        List<BuildPlan> plans=new ArrayList<>();Building start=null;
        for(int i=0;i<points.size;i++){
            var point=points.get(i);Tile tile=Vars.world.tile(point.x,point.y);
            if(tile==null)throw new IllegalStateException("st.build.error.placement");
            if(i>0){var previous=points.get(i-1);Tile before=Vars.world.tile(previous.x,previous.y);
                if(!node.overlaps(before,tile)||PowerNode.insulated(before,tile))throw new IllegalStateException("st.build.error.power-line");}
            if(i==points.size-1&&tile.build==target)continue;
            if(i==0&&tile.build!=null&&tile.build.team==unit.team&&tile.build.power!=null){
                start=tile.build;
                if(!start.block.connectedPower||node.sameBlockConnection&&start.block!=node
                        ||start.block instanceof PowerNode existing&&start.power.links.size>=existing.maxNodes)
                    throw new IllegalStateException("st.build.error.power-target");
                continue;
            }
            if(!Build.validPlace(node,unit.team,point.x,point.y,0))throw new IllegalStateException("st.build.error.placement");
            plans.add(new BuildPlan(point.x,point.y,0,node));
        }
        checkCount(plans.size());
        java.util.Collections.reverse(plans); // Each new native node can connect toward the existing target.
        return new PowerLine(target,start,node,plans);
    }

    public int enqueuePowerLine(PowerLine line){
        Unit unit=builder();
        if(line==null||line.world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
        if(!line.target.isValid()||line.target.team!=unit.team||line.target.power==null||!line.target.block.connectedPower
                ||line.node.sameBlockConnection&&line.target.block!=line.node
                ||line.target.block instanceof PowerNode node&&line.target.power.links.size>=node.maxNodes)
            throw new IllegalStateException("st.build.error.power-target");
        Tile previous=line.target.tile;
        for(BuildPlan plan:line.plans){
            Tile next=Vars.world.tile(plan.x,plan.y);
            if(plan.block!=line.node||next==null||!line.node.overlaps(previous,next)||PowerNode.insulated(previous,next))
                throw new IllegalStateException("st.build.error.power-line");
            previous=next;
        }
        if(line.start!=null && (!line.start.isValid()||line.start.team!=unit.team||line.start.power==null||!line.start.block.connectedPower
                ||line.node.sameBlockConnection&&line.start.block!=line.node
                ||line.start.block instanceof PowerNode startNode&&line.start.power.links.size>=startNode.maxNodes
                ||!line.node.overlaps(previous,line.start.tile)||PowerNode.insulated(previous,line.start.tile)))
            throw new IllegalStateException("st.build.error.power-target");
        return enqueue(line.plans);
    }

    public void withdraw(int x, int y, Item item) {
        requireNoPendingDrop();
        localUnit(); rate();
        Building target = itemTarget(x, y);
        if (item == null || !Vars.content.items().contains(item, true) || target.items == null || target.items.get(item) == 0
                || Vars.player.unit().maxAccepted(item) <= 0) throw new IllegalArgumentException("st.build.error.items");
        Call.requestItem(Vars.player, target, item, Vars.player.unit().maxAccepted(item));
    }
    public void deposit(int x, int y) {
        requireNoPendingDrop();
        localUnit(); rate();
        Building target = itemTarget(x, y);
        if (target.items == null || Vars.player.unit().stack.amount <= 0 || !target.allowDeposit())
            throw new IllegalArgumentException("st.build.error.items");
        Call.transferInventory(Vars.player, target);
    }
    public void drop() { requireNoPendingDrop(); localUnit(); rate(); if (Vars.player.unit().stack.amount > 0) Call.dropItem(Vars.player.unit().rotation); }

    /** Preview and delayed native request; no new network protocol or server privileges. */
    public static final class DropPreview {
        private final Object world;
        private final Building target;
        private final Unit unit;
        private final Item item;
        private final int available, amount;
        private DropPreview(Building target, Unit unit, Item item, int available, int amount) {
            world=Vars.world.tiles;this.target=target;this.unit=unit;this.item=item;this.available=available;this.amount=amount;
        }
        public String itemName(){return item.localizedName;}
        public int amount(){return amount;}
        public int x(){return target.tileX();}
        public int y(){return target.tileY();}
    }
    private record PendingDrop(DropPreview preview,long deadline) { }
    public boolean hasPendingDrop(){return pendingDrop!=null;}
    public String preferredDropStatus(){return dropStatus;}
    public void cancelPreferredDrop(){if(pendingDrop!=null){pendingDrop=null;dropStatus="st.build.priority-drop-cancelled";}}
    private void requireNoPendingDrop(){if(pendingDrop!=null)throw new IllegalStateException("st.build.priority-drop-busy");}
    public DropPreview previewPreferredDrop(int x,int y){
        requireNoPendingDrop();Unit unit=localUnit();Building target=itemTarget(x,y);
        if(unit.stack.amount!=0||target.items==null||target.items.empty())throw new IllegalArgumentException("st.build.error.items");
        Item item=target.items.has(mindustry.content.Items.sand)?mindustry.content.Items.sand
                :target.items.has(mindustry.content.Items.coal)?mindustry.content.Items.coal:target.items.first();
        if(item==null||!Vars.content.items().contains(item,true))throw new IllegalArgumentException("st.build.error.items");
        int available=target.items.get(item),amount=Math.min(available,unit.maxAccepted(item));
        if(amount<=0)throw new IllegalArgumentException("st.build.error.items");
        return new DropPreview(target,unit,item,available,amount);
    }
    public void beginPreferredDrop(DropPreview preview){
        requireNoPendingDrop();Unit unit=localUnit();
        if(preview==null||preview.world!=Vars.world.tiles||preview.unit!=unit||!preview.target.isValid()
                ||itemTarget(preview.x(),preview.y())!=preview.target||preview.target.items==null||unit.stack.amount!=0
                ||preview.target.items.get(preview.item)!=preview.available||unit.maxAccepted(preview.item)<preview.amount)
            throw new IllegalStateException("st.build.priority-drop-changed");
        rate();install();pendingDrop=new PendingDrop(preview,System.nanoTime()+5_000_000_000L);
        dropStatus="st.build.priority-drop-waiting";
        try{Call.requestItem(Vars.player,preview.target,preview.item,preview.amount);}
        catch(RuntimeException failure){pendingDrop=null;dropStatus="st.build.priority-drop-cancelled";throw failure;}
    }
    private void updatePreferredDrop(){
        PendingDrop pending=pendingDrop;if(pending==null)return;DropPreview preview=pending.preview;
        if(System.nanoTime()>pending.deadline||Vars.state==null||!Vars.state.isGame()||Vars.world.tiles!=preview.world
                ||Vars.player==null||Vars.player.dead()||Vars.player.unit()!=preview.unit||!preview.unit.isAdded()
                ||!preview.target.isValid()||!preview.target.interactable(Vars.player.team())
                ||!Vars.player.within(preview.target,Vars.itemTransferRange)){
            pendingDrop=null;dropStatus="st.build.priority-drop-cancelled";return;
        }
        if(preview.unit.stack.amount>0 && (preview.unit.stack.item!=preview.item||preview.unit.stack.amount>preview.amount)){
            pendingDrop=null;dropStatus="st.build.priority-drop-cancelled";return;
        }
        if(preview.unit.stack.amount!=preview.amount||preview.target.items==null
                ||preview.target.items.get(preview.item)!=preview.available-preview.amount)return;
        pendingDrop=null;
        try{Call.dropItem(preview.unit.rotation);dropStatus="st.build.priority-drop-sent";}
        catch(RuntimeException failure){dropStatus="st.build.priority-drop-cancelled";}
    }
    private static Building itemTarget(int x, int y) {
        Building target = Vars.world.build(x, y);
        if (target == null || !target.interactable(Vars.player.team()) || !Vars.player.within(target, Vars.itemTransferRange))
            throw new IllegalArgumentException("st.build.error.target");
        return target;
    }
    private void rate() {
        long now = System.nanoTime();
        if (lastRequest != 0 && now - lastRequest < 500_000_000L) throw new IllegalStateException("st.arena.error.rate");
        lastRequest = now;
    }
    private static void content(Block block) {
        if (block == null || !Vars.content.blocks().contains(block, true) || block.privileged || block.isBanned()
                || !block.isVisible() || !block.unlockedNow() || !block.supportsEnv(Vars.state.rules.env))
            throw new IllegalArgumentException("st.build.error.content");
    }
    public int removePlanCluster(int x, int y) {
        Unit unit = builder();
        if (unit.plans.size > MAX_PLANS) throw new IllegalStateException("st.build.error.count");
        List<BuildPlan> plans = new ArrayList<>();
        for (BuildPlan plan : unit.plans) plans.add(plan);
        BuildPlan seed = plans.stream().filter(p -> p.x == x && p.y == y).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("st.build.error.target"));
        Set<BuildPlan> connected = new HashSet<>();
        ArrayDeque<BuildPlan> pending = new ArrayDeque<>();
        connected.add(seed); pending.add(seed);
        Rect a = new Rect(), b = new Rect();
        while (!pending.isEmpty()) {
            BuildPlan next = pending.removeFirst();
            next.bounds(a).grow(0.1f);
            for (BuildPlan candidate : plans) if (!connected.contains(candidate) && a.overlaps(candidate.bounds(b))) {
                connected.add(candidate); pending.add(candidate);
            }
        }
        for (BuildPlan plan : connected) unit.removeBuild(plan.x, plan.y, plan.breaking);
        return connected.size();
    }
    private static void checkCount(int size) { if (size == 0 || size > MAX_PLANS) throw new IllegalArgumentException("st.build.error.count"); }
}
