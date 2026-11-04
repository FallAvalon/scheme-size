package org.schemetools.host;

import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.type.StatusEffect;
import mindustry.ctype.ContentType;
import mindustry.net.Packets.AdminAction;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

/** Host-authoritative mutations used by the organizer UI. */
public final class HostActions {
    public static final int MAX_ITEM_DELTA = 10000;
    public static final int MAX_UNITS = 25;
    public static final int GOD_BATCH_UNITS = 1000;
    private final boolean god;
    public HostActions(){this(false);}
    private final java.util.Map<UnitType,int[]> environments=new java.util.IdentityHashMap<>();
    private final java.util.Map<mindustry.world.Block,Integer> capacities=new java.util.IdentityHashMap<>();
    public HostActions(boolean god){
        this.god=god;
        if(god){
            arc.Events.on(mindustry.game.EventType.ResetEvent.class,event->restoreContent());
            arc.Events.on(mindustry.game.EventType.SaveLoadEvent.class,event->{if(!Vars.net.client())for(Unit unit:Groups.unit)allowEnvironment(unit.type);});
            arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class,event->{
                restoreContent();
                if(Vars.net.client())return;
                for(Unit unit:Groups.unit)allowEnvironment(unit.type);
                for(Team team:Team.all)for(CoreBuild core:team.cores()){
                    int max=core.block.itemCapacity;for(Item item:Vars.content.items())max=Math.max(max,core.items.get(item));
                    godCapacity(core,max);
                }
            });
        }
    }
    private void restoreContent(){
        environments.forEach((type,flags)->{type.envEnabled=flags[0];type.envDisabled=flags[1];});environments.clear();
        capacities.forEach((block,capacity)->block.itemCapacity=capacity);capacities.clear();
    }
    public boolean godMode(){return god;}
    public int maxSpawnBatch(){return god?GOD_BATCH_UNITS:MAX_UNITS;}
    /** Native host rules, also synchronized to vanilla guests. */
    private void godUnitRules(UnitType type){
        if(!Vars.state.rules.disableUnitCap){Vars.state.rules.disableUnitCap=true;Call.setRules(Vars.state.rules);}
        allowEnvironment(type);
        if(type.segmentUnit!=null)allowEnvironment(type.segmentUnit);
        if(type.segmentEndUnit!=null)allowEnvironment(type.segmentEndUnit);
    }
    private void allowEnvironment(UnitType type){
        environments.putIfAbsent(type,new int[]{type.envEnabled,type.envDisabled});
        // Content flags are restored at reset. Native entity types remain unchanged.
        type.envEnabled |= Vars.state.rules.env;
        type.envDisabled &= ~Vars.state.rules.env;
    }
    private void godCapacity(CoreBuild core,int amount){
        if(!god||amount<=core.storageCapacity)return;
        int count=0;for(CoreBuild other:core.team.cores())if(other.block==core.block)count++;
        if(count==0)throw new IllegalArgumentException("st.error.input");
        long increase=((long)amount-core.storageCapacity+count-1)/count;
        long next=(long)core.block.itemCapacity+increase;
        if(next>Integer.MAX_VALUE)throw new IllegalArgumentException("st.error.capacity");
        var totals=new java.util.IdentityHashMap<Team,Integer>();
        for(Team team:Team.all){
            int affected=0;for(CoreBuild other:team.cores())if(other.block==core.block)affected++;
            if(affected==0)continue;
            long capacity=(long)team.core().storageCapacity+increase*affected;
            if(capacity>Integer.MAX_VALUE)throw new IllegalArgumentException("st.error.capacity");
            totals.put(team,(int)capacity);
        }
        capacities.putIfAbsent(core.block,core.block.itemCapacity);core.block.itemCapacity=(int)next;
        totals.forEach((team,capacity)->{for(CoreBuild other:team.cores())other.storageCapacity=capacity;});
    }
    /** Physics still executes on native units: relocate invalid points instead of spawning doomed entities. */
    private float[] godSpawnPoint(Team team,UnitType type,float x,float y){
        Unit probe=type.create(team);var center=Vars.world.tileWorld(x,y);
        if(probe.canPass(center.x,center.y))return new float[]{x,y};
        int limit=Math.max(Vars.world.width(),Vars.world.height());
        for(int radius=1;radius<=limit;radius++){
            int minX=Math.max(0,center.x-radius),maxX=Math.min(Vars.world.width()-1,center.x+radius);
            int minY=Math.max(0,center.y-radius),maxY=Math.min(Vars.world.height()-1,center.y+radius);
            for(int tx=minX;tx<=maxX;tx++){
                if(center.y-radius>=0&&probe.canPass(tx,center.y-radius))return new float[]{tx*Vars.tilesize,(center.y-radius)*Vars.tilesize};
                if(center.y+radius<Vars.world.height()&&probe.canPass(tx,center.y+radius))return new float[]{tx*Vars.tilesize,(center.y+radius)*Vars.tilesize};
            }
            for(int ty=minY;ty<=maxY;ty++){
                if(center.x-radius>=0&&probe.canPass(center.x-radius,ty))return new float[]{(center.x-radius)*Vars.tilesize,ty*Vars.tilesize};
                if(center.x+radius<Vars.world.width()&&probe.canPass(center.x+radius,ty))return new float[]{(center.x+radius)*Vars.tilesize,ty*Vars.tilesize};
            }
        }
        throw new IllegalArgumentException("st.error.spawn-location");
    }

    public record ItemAdjustment(int before, int after) { }
    public record SpawnResult(int requested, int spawned) { }
    public enum BooleanRule {
        FOG, LIGHTING, BORDER_DARKNESS, SCHEMATICS, POSSESSION, WAVES, WAVE_TIMER,
        INFINITE_RESOURCES, INSTANT_BUILD, FIRE, REACTOR_EXPLOSIONS, UNIT_CAP_DISABLED;
        public boolean get(mindustry.game.Rules rules) { return switch (this) {
            case FOG -> rules.fog; case LIGHTING -> rules.lighting; case BORDER_DARKNESS -> rules.borderDarkness;
            case SCHEMATICS -> rules.schematicsAllowed; case POSSESSION -> rules.possessionAllowed;
            case WAVES -> rules.waves; case WAVE_TIMER -> rules.waveTimer; case INFINITE_RESOURCES -> rules.infiniteResources;
            case INSTANT_BUILD -> rules.instantBuild; case FIRE -> rules.fire; case REACTOR_EXPLOSIONS -> rules.reactorExplosions;
            case UNIT_CAP_DISABLED -> rules.disableUnitCap;
        }; }
        public void set(mindustry.game.Rules rules, boolean value) { switch (this) {
            case FOG -> rules.fog = value; case LIGHTING -> rules.lighting = value; case BORDER_DARKNESS -> rules.borderDarkness = value;
            case SCHEMATICS -> rules.schematicsAllowed = value; case POSSESSION -> rules.possessionAllowed = value;
            case WAVES -> rules.waves = value; case WAVE_TIMER -> rules.waveTimer = value;
            case INFINITE_RESOURCES -> rules.infiniteResources = value; case INSTANT_BUILD -> rules.instantBuild = value;
            case FIRE -> rules.fire = value; case REACTOR_EXPLOSIONS -> rules.reactorExplosions = value;
            case UNIT_CAP_DISABLED -> rules.disableUnitCap = value;
        } }
    }
    public enum NumericRule {
        BUILD_SPEED(0.05f, 100), BUILD_COST(0.05f, 100), UNIT_DAMAGE(0.05f, 100), UNIT_HEALTH(0.05f, 100),
        BLOCK_DAMAGE(0.05f, 100), BLOCK_HEALTH(0.05f, 100), WAVE_SECONDS(1, 3600), UNIT_CAP(0, 10000);
        public final float min, max;
        NumericRule(float min, float max) { this.min = min; this.max = max; }
        public float get(mindustry.game.Rules rules) { return switch (this) {
            case BUILD_SPEED -> rules.buildSpeedMultiplier; case BUILD_COST -> rules.buildCostMultiplier;
            case UNIT_DAMAGE -> rules.unitDamageMultiplier; case UNIT_HEALTH -> rules.unitHealthMultiplier;
            case BLOCK_DAMAGE -> rules.blockDamageMultiplier; case BLOCK_HEALTH -> rules.blockHealthMultiplier;
            case WAVE_SECONDS -> rules.waveSpacing / 60; case UNIT_CAP -> rules.unitCap;
        }; }
        public void set(mindustry.game.Rules rules, float value) { switch (this) {
            case BUILD_SPEED -> rules.buildSpeedMultiplier = value; case BUILD_COST -> rules.buildCostMultiplier = value;
            case UNIT_DAMAGE -> rules.unitDamageMultiplier = value; case UNIT_HEALTH -> rules.unitHealthMultiplier = value;
            case BLOCK_DAMAGE -> rules.blockDamageMultiplier = value; case BLOCK_HEALTH -> rules.blockHealthMultiplier = value;
            case WAVE_SECONDS -> rules.waveSpacing = value * 60; case UNIT_CAP -> rules.unitCap = (int)value;
        } }
    }
    private long lastRuleChange;
    private long lastLoadoutChange;
    private final java.util.Map<String, Team> cycling = new java.util.HashMap<>();
    private java.util.function.BooleanSupplier teamCyclesAllowed = () -> true;
    public void allowTeamCyclesWhen(java.util.function.BooleanSupplier condition) { teamCyclesAllowed = java.util.Objects.requireNonNull(condition); }
    public boolean isCycling(Player player) { return player != null && cycling.containsKey(player.uuid()); }
    public void cycleTeam(Player player, boolean enabled) {
        connected(player);
        if (!enabled) {
            Team original = cycling.remove(player.uuid());
            if (original != null) team(player, original);
            return;
        }
        if (!teamCyclesAllowed.getAsBoolean() || !isValidTeam(player.team()) || cycling.size() >= 16 || isCycling(player))
            throw new IllegalStateException("st.error.input");
        cycling.put(player.uuid(), player.team());
        Object world = Vars.world.tiles;
        String uuid = player.uuid();
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!cycling.containsKey(uuid)) return;
            if (!isAvailable() || Vars.world.tiles != world || !Groups.player.contains(p -> p == player)) { cycling.remove(uuid); return; }
            Team[] teams = Team.baseTeams;
            int index = 0;
            for (int i = 0; i < teams.length; i++) if (teams[i] == player.team()) index = i;
            Team next = teams[(index + 1) % teams.length];
            if (!isValidTeam(next)) next = teams[1];
            team(player, next); arc.util.Time.runTask(60f, tick[0]);
        };
        arc.util.Time.runTask(60f, tick[0]);
    }
    public void stopTeamCycles() {
        if (isAvailable()) for (Player player : Groups.player.copy()) if (isCycling(player)) cycleTeam(player, false);
        cycling.clear();
    }
    private void rateRules() {
        if(god)return;
        long now = System.nanoTime();
        if (lastRuleChange != 0 && now - lastRuleChange < 250_000_000L) throw new IllegalStateException("st.arena.error.rate");
        lastRuleChange = now;
    }
    public void rule(BooleanRule key, boolean value) {
        requireAuthority();
        if (key == null) throw new IllegalArgumentException("st.error.input");
        rateRules(); var rules = Vars.state.rules; boolean before = key.get(rules); key.set(rules, value);
        try { Call.setRules(rules); }
        catch (RuntimeException failure) { key.set(rules, before); throw failure; }
    }
    public void rule(NumericRule key, float value) {
        requireAuthority();
        if (key == null || !Float.isFinite(value) || value < key.min || value > key.max
                || key == NumericRule.UNIT_CAP && value != (int)value) throw new IllegalArgumentException("st.error.input");
        rateRules(); var rules = Vars.state.rules; float before = key.get(rules); key.set(rules, value);
        try { Call.setRules(rules); }
        catch (RuntimeException failure) { key.set(rules, before); throw failure; }
    }

    public boolean hasGame() {
        return Vars.state != null && Vars.world != null && Vars.state.isGame();
    }

    /** True only for a local game or the authoritative built-in host, never a vanilla client. */
    public boolean isAvailable() {
        return hasGame() && !Vars.net.client();
    }

    private void requireAuthority() {
        if (!hasGame()) throw new IllegalStateException("st.error.no-game");
        if (Vars.net.client()) throw new IllegalStateException("st.error.authority");
    }

    public static boolean isValidTeam(Team team) {
        return team != null && team.id > 0 && team.id < Team.all.length && Team.all[team.id] == team;
    }

    /** Boss presets only use the teams exposed by the standard team picker. */
    public static boolean isAccessiblePresetTeam(Team team) {
        if (!isValidTeam(team) || team == Team.derelict) return false;
        for (Team base : Team.baseTeams) if (base == team) return true;
        return false;
    }

    public ItemAdjustment adjustItem(Team team, CoreBuild core, Item item, int delta) {
        requireAuthority();
        if (!isValidTeam(team) || core == null || item == null
                || !Vars.content.items().contains(item, true) || delta == 0
                || (!god && Math.abs((long)delta) > MAX_ITEM_DELTA) || !containsCore(team, core))
            throw new IllegalArgumentException("st.error.input");

        int current = core.items.get(item);
        long target = (long)current + delta;
        if (target < 0 || target > Integer.MAX_VALUE || (!god && target > core.getMaximumAccepted(item)))
            throw new IllegalArgumentException("st.error.capacity");
        long total=target;for(Item other:Vars.content.items())if(other!=item)total+=core.items.get(other);
        if(total>Integer.MAX_VALUE)throw new IllegalArgumentException("st.error.capacity");
        godCapacity(core,(int)target);
        core.items.set(item, (int)target);
        return new ItemAdjustment(current, core.items.get(item));
    }

    private static boolean containsCore(Team team, CoreBuild candidate) {
        if (candidate.team != team) return false;
        var cores = team.cores();
        for (int i = 0; i < cores.size; i++) if (cores.get(i) == candidate) return true;
        return false;
    }

    /** Sets the explicitly listed resources only; validates the complete batch before any mutation. */
    public void setCoreItems(Team team, CoreBuild core, java.util.Map<Item,Integer> amounts) {
        requireAuthority();
        if (!isValidTeam(team) || core == null || !core.isValid() || !containsCore(team, core)
                || amounts == null || amounts.isEmpty() || amounts.size() > 64) throw new IllegalArgumentException("st.error.input");
        var target = new java.util.LinkedHashMap<>(amounts);
        var previous = new java.util.LinkedHashMap<Item,Integer>();
        for (var entry : target.entrySet()) {
            Item item=entry.getKey();Integer amount=entry.getValue();
            if(item==null || !Vars.content.items().contains(item,true) || amount==null || amount<0 || (!god && amount>MAX_ITEM_DELTA))
                throw new IllegalArgumentException("st.error.input");
            if(!god && amount>core.getMaximumAccepted(item))throw new IllegalArgumentException("st.error.capacity");
            previous.put(item,core.items.get(item));
        }
        long now=System.nanoTime();
        if(!god && lastLoadoutChange!=0 && now-lastLoadoutChange<250_000_000L)throw new IllegalStateException("st.arena.error.rate");
        try {
            if(god){long total=0;int max=0;for(Item item:Vars.content.items()){int amount=target.getOrDefault(item,core.items.get(item));total+=amount;max=Math.max(max,amount);}if(total>Integer.MAX_VALUE)throw new IllegalArgumentException("st.error.capacity");godCapacity(core,max);}
            target.forEach(core.items::set); }
        catch(RuntimeException failure){previous.forEach(core.items::set);throw failure;}
        lastLoadoutChange=now;
    }

    /** Creates a small ring of units near the requested world position. */
    public SpawnResult spawn(Team team, UnitType type, float x, float y, int count) {
        requireAuthority();
        if (!isValidTeam(team) || type == null || !Vars.content.units().contains(type, true)
                || !Float.isFinite(x) || !Float.isFinite(y) || count < 1 || count > maxSpawnBatch()
                || Vars.world.tileWorld(x, y) == null)
            throw new IllegalArgumentException("st.error.input");

        // Segmented types create extra entities; keep spawn accounting exact until they are supported.
        if (!god && (type.isBanned() || type.segmentUnits > 1 || type.isHidden() || !type.supportsEnv(Vars.state.rules.env)
                || (type.useUnitCap && !Vars.state.rules.disableUnitCap
                    && (long)team.data().countType(type) + count > Units.getCap(team))))
            throw new IllegalStateException("st.error.unit-cap");

        if(god){
            float[] point=godSpawnPoint(team,type,x,y);
            godUnitRules(type);
            java.util.List<Unit> created=new java.util.ArrayList<>();
            try{
                for(int i=0;i<count;i++)type.spawn(team,point[0],point[1],90f,created::add);
            }catch(RuntimeException failure){
                for(Unit unit:created)if(unit.isAdded())try{Call.unitDespawn(unit);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
            return new SpawnResult(count,count);
        }
        float radius = Math.max(Vars.tilesize, type.hitSize * 0.65f);
        float[] xs = new float[count];
        float[] ys = new float[count];
        java.util.List<Unit> units = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2d * i / Math.max(1, count);
            xs[i] = x + (float)Math.cos(angle) * radius;
            ys[i] = y + (float)Math.sin(angle) * radius;
            var tile = Vars.world.tileWorld(xs[i], ys[i]);
            Unit unit = type.create(team);
            if (unit == null || tile == null || !unit.canPass(tile.x, tile.y))
                throw new IllegalArgumentException("st.error.spawn-location");
            unit.set(xs[i], ys[i]);
            units.add(unit);
        }

        int spawned = 0;
        try {
            for (int i = 0; i < count; i++) {
                units.get(i).add();
                spawned++;
            }
        } catch (RuntimeException failure) {
            int remaining = 0;
            for (Unit unit : units) if (unit.isAdded()) {
                try { Call.unitDespawn(unit); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); remaining++; }
            }
            if (remaining > 0) throw new PartialSpawnException(remaining, count, failure);
            throw failure;
        }
        return new SpawnResult(count, spawned);
    }

    public void team(Player target, Team team) {
        requireAuthority();
        if (target == null || !isValidTeam(team) || !Groups.player.contains(p -> p == target))
            throw new IllegalArgumentException("st.error.input");
        target.team(team);
    }

    /** Validates placement using the actual unit's vanilla movement rules before adding it. */
    public Unit controlledUnit(Player target, UnitType type, float x, float y) {
        requireAuthority();
        if (target == null || !Groups.player.contains(p -> p == target) || !isValidTeam(target.team())
                || type == null || !Vars.content.units().contains(type, true) || !type.playerControllable
                || (!god && (type.isHidden() || type.isBanned() || type.segmentUnits > 1
                    || !type.supportsEnv(Vars.state.rules.env) || !Vars.state.rules.possessionAllowed))
                || !Float.isFinite(x) || !Float.isFinite(y) || Vars.world.tileWorld(x, y) == null)
            throw new IllegalArgumentException("st.error.input");
        if(god){
            godUnitRules(type);
            if(!Vars.state.rules.possessionAllowed){Vars.state.rules.possessionAllowed=true;Call.setRules(Vars.state.rules);}
            java.util.List<Unit> created=new java.util.ArrayList<>();
            try{
                float[] point=godSpawnPoint(target.team(),type,x,y);
                Unit root=type.spawn(target.team(),point[0],point[1],90f,created::add);Call.unitControl(target,root);
                if(target.unit()!=root)throw new IllegalStateException("st.error.control-refused");return root;
            }catch(RuntimeException failure){for(Unit unit:created)if(unit.isAdded())Call.unitDespawn(unit);throw failure;}
        }
        if (type.useUnitCap && !Units.canCreate(target.team(), type))
            throw new IllegalStateException("st.error.unit-cap");
        Unit unit = type.create(target.team());
        boolean found = false;
        int centerX = mindustry.core.World.toTile(x), centerY = mindustry.core.World.toTile(y);
        for (int radius = 0; radius <= 16 && !found; radius++) {
            for (int dx = -radius; dx <= radius && !found; dx++) for (int dy = -radius; dy <= radius; dy++) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                int tx = centerX + dx, ty = centerY + dy;
                if (Vars.world.tile(tx, ty) != null && unit.canPass(tx, ty)) {
                    unit.set(tx * Vars.tilesize, ty * Vars.tilesize);
                    found = true;
                    break;
                }
            }
        }
        if (!found) throw new IllegalArgumentException("st.error.spawn-location");
        unit.add();
        try {
            Call.unitControl(target, unit);
            if (target.unit() != unit) throw new IllegalStateException("st.error.control-refused");
        } catch (RuntimeException failure) {
            Call.unitDespawn(unit);
            throw failure;
        }
        return unit;
    }

    public void waves(boolean enabled) {
        requireAuthority();
        Vars.state.rules.waves = enabled;
        Call.setRules(Vars.state.rules);
    }

    private void connected(Player target) {
        requireAuthority();
        if (target == null || !Groups.player.contains(p -> p == target)) throw new IllegalArgumentException("st.error.input");
    }

    public void teleport(Player target, float x, float y) {
        connected(target);
        var tile = Float.isFinite(x) && Float.isFinite(y) ? Vars.world.tileWorld(x, y) : null;
        if (target.dead() || tile == null || (!god && !target.unit().canPass(tile.x, tile.y)))
            throw new IllegalArgumentException("st.error.spawn-location");
        target.unit().set(x, y);
        target.unit().vel.setZero();
        target.unit().snapInterpolation();
        target.set(x, y);
        target.snapInterpolation();
        if (target.con != null) Call.setPosition(target.con, x, y);
    }

    public void removeUnit(Player target) {
        connected(target);
        if (target.dead()) throw new IllegalArgumentException("st.error.input");
        Call.unitDespawn(target.unit());
    }
    public void despawn(Unit target) {
        requireAuthority();
        if (target == null || !target.isAdded() || Groups.unit.getByID(target.id) != target || (!god && target.type.segmentUnits > 1))
            throw new IllegalArgumentException("st.error.input");
        if(god && target instanceof mindustry.gen.Segmentc segment){
            var head=segment.headSegment();if(head==null)head=segment;
            java.util.Set<mindustry.gen.Segmentc> chain=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for(var part=head;part!=null&&chain.add(part);part=part.childSegment()){}
            for(var part:chain)if(part.isAdded())Call.unitDespawn((Unit)part);
        }else Call.unitDespawn(target);
    }

    /** Native deaths (explosions/drops/events), protecting every currently player-controlled chain. */
    public int killUnits(java.util.Collection<Unit> requested) {
        requireAuthority();
        if(requested==null||requested.stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("st.error.input");
        return killUnits(new UnitScope.Request(Vars.world.tiles,UnitScope.all(),requested.stream().map(UnitScope.Target::capture).toList()));
    }
    public int killUnits(UnitScope.Request request){
        requireAuthority();
        if(request==null||request.world()!=Vars.world.tiles)throw new IllegalArgumentException("st.error.input");
        request.scope().validate();
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Unit,Boolean>());
        int killed=0;
        for(var target:request.targets()){
            Unit unit=target.unit();
            if(!target.current()||!seen.add(unit)||!request.scope().matches(unit)||protectedUnit(unit))continue;
            unit.kill();if(unit.dead||!unit.isAdded())killed++;
        }
        return killed;
    }
    public static boolean protectedUnit(Unit unit){
        if(unit==null)return true;
        if(playerUnit(unit))return true;
        if(unit instanceof mindustry.gen.Segmentc segment){
            var head=segment.headSegment();if(head==null)head=segment;
            var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<mindustry.gen.Segmentc,Boolean>());
            for(var part=head;part!=null && seen.add(part);part=part.childSegment())if(playerUnit((Unit)part))return true;
        }
        return false;
    }
    private static boolean playerUnit(Unit unit){
        if(unit.isPlayer())return true;
        for(Player player:Groups.player)if(player.unit()==unit)return true;
        return false;
    }

    public void status(Player target, StatusEffect effect, float seconds, boolean remove) {
        connected(target);
        if (target.dead() || effect == null || Vars.content.getBy(ContentType.status).indexOf(effect, true) < 0
                || effect.dynamic || effect.reactive || !Float.isFinite(seconds) || seconds < 0.1f || seconds > 3600)
            throw new IllegalArgumentException("st.error.input");
        if (remove) target.unit().unapply(effect);
        else {
            if (target.unit().isImmune(effect)) throw new IllegalArgumentException("st.error.effect-immune");
            target.unit().apply(effect, seconds * 60f);
        }
    }

    public void admin(Player target, boolean enabled) {
        connected(target);
        if (!Vars.net.server() || target.isLocal() || target.con == null || target.usid() == null)
            throw new IllegalArgumentException("st.error.remote-player");
        if (enabled) Vars.netServer.admins.adminPlayer(target.uuid(), target.usid());
        else Vars.netServer.admins.unAdminPlayer(target.uuid());
        target.admin = enabled;
    }

    public void moderate(Player target, AdminAction action) {
        connected(target);
        if (!Vars.net.server() || target.isLocal() || target.con == null
                || (action != AdminAction.kick && action != AdminAction.ban && action != AdminAction.trace))
            throw new IllegalArgumentException("st.error.remote-player");
        // Same entry point as Mindustry's player list, with its own native server validation.
        Call.adminRequest(target, action, null);
    }

    public void coreShard(Team team, int x, int y) {
        requireAuthority();
        if (!isValidTeam(team) || Vars.world.tile(x, y) == null) throw new IllegalArgumentException("st.error.input");
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            var tile = Vars.world.tile(x + dx, y + dy);
            if (tile == null || tile.block() != mindustry.content.Blocks.air || tile.floor().isLiquid || tile.floor().solid)
                throw new IllegalArgumentException("st.error.core-area");
        }
        var bounds = new arc.math.geom.Rect(x * Vars.tilesize - 12, y * Vars.tilesize - 12, 24, 24);
        if (Groups.unit.contains(unit -> bounds.contains(unit.x, unit.y))) throw new IllegalArgumentException("st.error.core-area");
        Vars.world.tile(x, y).setNet(mindustry.content.Blocks.coreShard, team, 0);
    }

    public void removeCore(Team team, int x, int y) {
        requireAuthority();
        var tile = Vars.world.tile(x, y);
        if (!isValidTeam(team) || tile == null || !(tile.build instanceof CoreBuild core) || core.team != team
                || core.block != mindustry.content.Blocks.coreShard)
            throw new IllegalArgumentException("st.error.input");
        // Contents are shared across team cores. Never silently erase the final inventory.
        if (team.cores().size <= 1 && core.items.total() > 0) throw new IllegalStateException("st.error.core-items");
        core.tile.removeNet();
    }

    public void announce(String message) {
        requireAuthority();
        if (message == null || message.isBlank() || message.codePointCount(0, message.length()) > 200)
            throw new IllegalArgumentException("st.error.input");
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t')
                throw new IllegalArgumentException("st.error.input");
        }
        Call.sendMessage(message.strip());
    }

    public void reset() {
        cycling.clear(); lastRuleChange = 0; lastLoadoutChange = 0;
    }

    public static final class PartialSpawnException extends RuntimeException {
        private final int spawned;
        private final int requested;

        public PartialSpawnException(int spawned, int requested, Throwable cause) {
            super("st.error.spawn-partial", cause);
            this.spawned = spawned;
            this.requested = requested;
        }

        public int spawned() { return spawned; }
        public int requested() { return requested; }
    }
}
