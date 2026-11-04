package org.schemetools.client;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.game.EventType.*;
import org.schemetools.Edition;

import java.util.HashMap;
import java.util.Map;

/** Optional metadata only. These native string packets never request world changes or execute code. */
public final class PeerInfo {
    private static final String HELLO = "scheme-size-meta-v1", REPLY = "scheme-size-meta-reply-v1";
    private static final Map<Integer, String> peers = new HashMap<>();
    private static final Map<Integer, Long> received = new HashMap<>();
    private static Edition localEdition;
    private static long lastHello;
    private static final Map<Integer,String> legacy=new HashMap<>();
    private static final Map<Integer,Long> legacyReceived=new HashMap<>();
    private static long legacyHello,lastBroadcast;
    private static boolean broadcastPending;
    private PeerInfo() { }
    public static boolean enabled() { return Core.settings.getBool("schemesize-share-subtitle", false); }
    public static String subtitle(String input) {
        return metadataText(input,false);
    }
    private static String metadataText(String input,boolean legacyWire) {
        String value = arc.util.Strings.stripColors(input == null ? "" : input);
        if (value.length() > 80 || value.chars().anyMatch(c -> Character.isISOControl(c) || !legacyWire && c == '|'))
            throw new IllegalArgumentException("st.error.input");
        return value;
    }
    private static String ownSubtitle(){try{return subtitle(Core.settings.getString("schemesize-subtitle",""));}catch(RuntimeException invalidSetting){return "";}}
    private static String outgoing() {
        return "1|" + Vars.player.id + "|" + localEdition.name() + "|" + ownSubtitle();
    }
    private static String parse(String value, int expectedId) {
        if (value == null || value.length() > 128) throw new IllegalArgumentException();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4 || !parts[0].equals("1") || Integer.parseInt(parts[1]) != expectedId
                || !(parts[2].equals("PLUS") || parts[2].equals("GOD"))) throw new IllegalArgumentException();
        return "Scheme-Size " + (parts[2].equals("PLUS") ? "Plus" : "God") + (parts[3].isBlank() ? "" : " · " + subtitle(parts[3]));
    }
    public static void install(Edition edition) {
        if (Vars.headless) return; localEdition = edition;
        Events.on(ResetEvent.class, event -> { peers.clear(); received.clear(); legacy.clear();legacyReceived.clear();lastHello=legacyHello=lastBroadcast=0;broadcastPending=false; });
        Events.on(PlayerLeave.class, event -> { peers.remove(event.player.id); received.remove(event.player.id);legacy.remove(event.player.id);legacyReceived.remove(event.player.id);broadcastPending=true; });
        Events.run(Trigger.update,()->{if(broadcastPending&&enabled()&&Vars.net.server()&&System.nanoTime()-lastBroadcast>=500_000_000L)broadcastLegacy();});
        Events.on(PlayerJoin.class, event -> arc.util.Time.runTask(60f, () -> {
            if (enabled() && Vars.net.server() && Groups.player.contains(player -> player == event.player) && event.player.con != null)
                {Call.clientPacketReliable(event.player.con, HELLO, outgoing());Call.clientPacketReliable(event.player.con,"SendMeSubtitle",Integer.toString(Vars.player.id));}
        }));
        Vars.netClient.addPacketHandler(HELLO, data -> {
            if (!enabled() || !Vars.net.client() || data == null || data.length() > 128 || lastHello != 0 && System.nanoTime() - lastHello < 2_000_000_000L) return;
            try {
                String[] parts = data.split("\\|", -1); int id = Integer.parseInt(parts[1]);
                if (!Groups.player.contains(player -> player.id == id)) return;
                String description = parse(data, id); lastHello = System.nanoTime(); peers.put(id, description);
                Call.serverPacketReliable(REPLY, outgoing());
            } catch (RuntimeException ignored) { }
        });
        Vars.netServer.addPacketHandler(REPLY, (player, data) -> {
            if (!enabled() || !Vars.net.server() || player == null || !Groups.player.contains(value -> value == player)) return;
            long now = System.nanoTime(), before = received.getOrDefault(player.id, 0L);
            if (before != 0 && now - before < 2_000_000_000L) return;
            received.put(player.id, now);
            try { peers.put(player.id, parse(data, player.id)); } catch (RuntimeException ignored) { }
        });
        // Independently implemented O279 wire adapter. Only bounded, opt-in text; never code or authority.
        Vars.netClient.addPacketHandler("SendMeSubtitle",data->{
            if(!enabled()||!Vars.net.client()||legacyHello!=0&&System.nanoTime()-legacyHello<2_000_000_000L)return;
            try{
                if(data!=null){if(data.length()>12)throw new IllegalArgumentException();int id=Integer.parseInt(data);
                    if(id < -1)return;}
                legacyHello=System.nanoTime();Call.serverPacketReliable("MySubtitle",ownSubtitle());
            }catch(RuntimeException ignored){}
        });
        Vars.netServer.addPacketHandler("MySubtitle",(player,data)->{
            if(!enabled()||!Vars.net.server()||player==null||!Groups.player.contains(value->value==player))return;
            long now=System.nanoTime(),before=legacyReceived.getOrDefault(player.id,0L);
            if(before!=0&&now-before<2_000_000_000L)return;legacyReceived.put(player.id,now);
            try{if(data==null)throw new IllegalArgumentException();legacy.put(player.id,metadataText(data,true));broadcastPending=true;}catch(RuntimeException ignored){}
        });
        Vars.netClient.addPacketHandler("Subtitles",data->{
            if(!enabled()||!Vars.net.client())return;
            try{Map<Integer,String> parsed=parseLegacy(data);legacy.clear();legacy.putAll(parsed);}catch(RuntimeException ignored){}
        });
    }
    private static Map<Integer,String> parseLegacy(String data){
        if(data==null||data.length()>65536)throw new IllegalArgumentException();
        // IntMap may encode strings as {class:java.lang.String,value:...}. Never deserialize class tags.
        boolean quoted=false,escape=false;int depth=0;
        for(char ch:data.toCharArray()){
            if(quoted){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')quoted=false;}
            else if(ch=='"')quoted=true;else if(ch=='{'||ch=='['){if(++depth>2)throw new IllegalArgumentException();}
            else if(ch=='}'||ch==']'){if(--depth<0)throw new IllegalArgumentException();}
        }
        if(quoted||depth!=0)throw new IllegalArgumentException();
        var json=new arc.util.serialization.JsonReader().parse(data);
        if(!json.isObject()||json.size>256)throw new IllegalArgumentException();
        Map<Integer,String> parsed=new HashMap<>();java.util.Set<Integer> seen=new java.util.HashSet<>();
        for(var entry=json.child;entry!=null;entry=entry.next){
            int id=Integer.parseInt(entry.name);if(!seen.add(id))throw new IllegalArgumentException();
            String raw;
            if(entry.isString())raw=entry.asString();
            else if(entry.isObject()&&entry.size==2&&entry.get("class")!=null&&entry.get("class").isString()
                    &&entry.getString("class").equals("java.lang.String")&&entry.get("value")!=null&&entry.get("value").isString())raw=entry.getString("value");
            else throw new IllegalArgumentException();
            String value=metadataText(raw,true);
            if(id<0)throw new IllegalArgumentException();parsed.put(id,value);
        }
        return parsed;
    }
    private static void broadcastLegacy(){
        broadcastPending=false;lastBroadcast=System.nanoTime();
        arc.struct.IntMap<String> values=new arc.struct.IntMap<>();values.put(Vars.player.id,ownSubtitle());
        int count=1;for(var entry:legacy.entrySet())if(count++<256&&Groups.player.contains(player->player.id==entry.getKey()))values.put(entry.getKey(),entry.getValue());
        String json=mindustry.io.JsonIO.write(values);int connections=0;
        for(var connection:Vars.net.getConnections())if(connections++<256)Call.clientPacketReliable(connection,"Subtitles",json);
    }
    public static void refresh() {
        peers.clear(); received.clear();legacy.clear();legacyReceived.clear();broadcastPending=false;
        if (!enabled() || !Vars.net.server() || localEdition == null) return;
        int count = 0;
        for (var connection : Vars.net.getConnections()) if (count++ < 256) {Call.clientPacketReliable(connection, HELLO, outgoing());Call.clientPacketReliable(connection,"SendMeSubtitle",Integer.toString(Vars.player.id));}
    }
    public static String describe(Player player) {
        if (player.isLocal() && localEdition != null) return "Scheme-Size " + (localEdition == Edition.GOD ? "God" : "Plus");
        if(!enabled())return Core.bundle.get("st.peer.unknown");
        return peers.getOrDefault(player.id,legacy.containsKey(player.id)?"Scheme-Size (legacy)"+(legacy.get(player.id).isBlank()?"":" · "+legacy.get(player.id)):Core.bundle.get("st.peer.unknown"));
    }
}
