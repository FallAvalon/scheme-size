package org.schemetools.client;

import arc.Core;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.type.Item;
import org.schemetools.host.HostActions;
import java.util.*;

/** Versioned local settings, content names rather than volatile numeric IDs. */
public final class ResourceLoadouts {
    private static final String KEY="schemesize-resource-loadouts-v1";
    public record Preset(String name,Map<Item,Integer> items) {
        public Preset {
            name=name==null?"":arc.util.Strings.stripColors(name.strip());
            if(name.isEmpty()||name.length()>48||name.chars().anyMatch(Character::isISOControl)
                    ||items==null||items.isEmpty()||items.size()>64)throw new IllegalArgumentException("st.error.input");
            var copy=new LinkedHashMap<Item,Integer>();
            for(var entry:items.entrySet()){
                if(entry.getKey()==null||!Vars.content.items().contains(entry.getKey(),true)||entry.getValue()==null
                        ||entry.getValue()<0)throw new IllegalArgumentException("st.error.input");
                copy.put(entry.getKey(),entry.getValue());
            }
            items=Collections.unmodifiableMap(copy);
        }
    }
    private ResourceLoadouts(){}
    public static List<Preset> decode(String text){
        if(text==null||text.length()>65536)throw new IllegalArgumentException("st.error.input");
        Jval data=Jval.read(text);
        if(!data.get("version").isNumber()||data.get("version").asDouble()!=1||data.get("presets").asArray().size>32)throw new IllegalArgumentException("st.error.input");
        var result=new ArrayList<Preset>();var names=new HashSet<String>();
        for(Jval value:data.get("presets").asArray()){
            var items=new LinkedHashMap<Item,Integer>();
            if(value.get("items").asArray().size>64)throw new IllegalArgumentException("st.error.input");
            for(Jval pair:value.get("items").asArray()){
                Item item=Vars.content.item(pair.getString("item"));Jval number=pair.get("amount");
                if(!number.isNumber()||number.asDouble()!=number.asInt()||items.put(item,number.asInt())!=null)
                    throw new IllegalArgumentException("st.error.input");
            }
            Preset preset=new Preset(value.getString("name"),items);
            if(!names.add(preset.name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("st.error.input");result.add(preset);
        }
        return List.copyOf(result);
    }
    public static String encode(List<Preset> presets){
        if(presets.size()>32)throw new IllegalArgumentException("st.error.input");
        Jval list=Jval.newArray();
        for(Preset preset:presets){
            Jval items=Jval.newArray();
            preset.items.forEach((item,amount)->items.add(Jval.newObject().put("item",item.name).put("amount",amount)));
            list.add(Jval.newObject().put("name",preset.name).put("items",items));
        }
        String text=Jval.newObject().put("version",1).put("presets",list).toString(Jval.Jformat.plain);
        decode(text);return text;
    }
    public static List<Preset> list(){return decode(Core.settings.getString(KEY,"{version:1,presets:[]}"));}
    public static void save(Preset preset){
        var all=new ArrayList<>(list());all.removeIf(value->value.name.equalsIgnoreCase(preset.name));all.add(preset);
        Core.settings.put(KEY,encode(all));
    }
    public static void remove(String name){var all=new ArrayList<>(list());all.removeIf(value->value.name.equalsIgnoreCase(name));Core.settings.put(KEY,encode(all));}
}
