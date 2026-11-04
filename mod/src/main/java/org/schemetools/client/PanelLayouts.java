package org.schemetools.client;

import arc.Core;
import arc.util.serialization.Jval;
import java.util.*;

/** Named, versioned local panel layouts. Whitelisted UI settings only; no world/admin/privacy settings. */
public final class PanelLayouts {
    private static final String KEY="schemesize-panel-layouts-v1";
    private record Field(int min,int max,int fallback,boolean bool){}
    private static final Map<String,Field> FIELDS=new LinkedHashMap<>();
    static{
        FIELDS.put("schemesize-hud-left",new Field(0,600,8,false));FIELDS.put("schemesize-hud-top",new Field(0,600,96,false));
        for(String key:new String[]{"pan-speed","zoom-in","zoom-out"})FIELDS.put("schemesize-"+key,new Field(1,5,1,false));
        bool("schemetools-hud",true);bool("schemesize-view-custom",false);
        for(String key:new String[]{"schematics","build","info","routes","visual","ai","console","players","arena"})bool("schemesize-module-"+key,true);
        for(var option:VisualLayers.Option.values())bool(option.key,false);
    }
    private static void bool(String key,boolean fallback){FIELDS.put(key,new Field(0,1,fallback?1:0,true));}
    public record Layout(String name,Map<String,Integer> values){
        public Layout{
            name=name==null?"":arc.util.Strings.stripColors(name.strip());
            if(name.isEmpty()||name.length()>48||name.chars().anyMatch(Character::isISOControl)||values==null||values.size()!=FIELDS.size())throw new IllegalArgumentException("st.error.input");
            for(var field:FIELDS.entrySet()){
                Integer value=values.get(field.getKey());if(value==null||value<field.getValue().min||value>field.getValue().max)throw new IllegalArgumentException("st.error.input");
            }
            values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
    private PanelLayouts(){}
    public static Layout capture(String name){
        Map<String,Integer> values=new LinkedHashMap<>();
        FIELDS.forEach((key,field)->values.put(key,field.bool?(Core.settings.getBool(key,field.fallback==1)?1:0):Core.settings.getInt(key,field.fallback)));
        return new Layout(name,values);
    }
    public static List<Layout> decode(String text){
        if(text==null||text.length()>65536)throw new IllegalArgumentException("st.error.input");
        // Bounded local format, reject deeply nested corrupt settings before invoking the parser.
        boolean quote=false,escape=false;int depth=0;
        for(char ch:text.toCharArray()){if(quote){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')quote=false;}
            else if(ch=='"')quote=true;else if(ch=='{'||ch=='['){if(++depth>4)throw new IllegalArgumentException("st.error.input");}else if(ch=='}'||ch==']'){if(--depth<0)throw new IllegalArgumentException("st.error.input");}}
        if(quote||depth!=0)throw new IllegalArgumentException("st.error.input");
        Jval root=Jval.read(text);if(!root.get("version").isNumber()||root.get("version").asDouble()!=1||root.get("layouts").asArray().size>16)throw new IllegalArgumentException("st.error.input");
        List<Layout> layouts=new ArrayList<>();Set<String> names=new HashSet<>();
        for(Jval value:root.get("layouts").asArray()){
            Map<String,Integer> fields=new LinkedHashMap<>();
            for(var field:value.get("values").asObject()){
                if(!field.value.isNumber()||field.value.asDouble()!=Math.rint(field.value.asDouble())||field.value.asDouble()<0||field.value.asDouble()>600)throw new IllegalArgumentException("st.error.input");
                fields.put(field.key,field.value.asInt());
            }
            Layout layout=new Layout(value.getString("name"),fields);
            if(!names.add(layout.name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("st.error.input");layouts.add(layout);
        }
        return List.copyOf(layouts);
    }
    public static String encode(List<Layout> layouts){
        Jval list=Jval.newArray();for(Layout layout:layouts){Jval values=Jval.newObject();layout.values.forEach(values::put);list.add(Jval.newObject().put("name",layout.name).put("values",values));}
        String text=Jval.newObject().put("version",1).put("layouts",list).toString(Jval.Jformat.formatted);decode(text);return text;
    }
    public static List<Layout> list(){return decode(Core.settings.getString(KEY,"{version:1,layouts:[]}"));}
    public static void save(Layout layout){var all=new ArrayList<>(list());all.removeIf(value->value.name.equalsIgnoreCase(layout.name));all.add(layout);Core.settings.put(KEY,encode(all));}
    public static void remove(String name){var all=new ArrayList<>(list());all.removeIf(value->value.name.equalsIgnoreCase(name));Core.settings.put(KEY,encode(all));}
    public static void apply(Layout layout){
        Layout checked=new Layout(layout.name,layout.values); // Validate everything before the first setting write.
        checked.values.forEach((key,value)->{if(FIELDS.get(key).bool)Core.settings.put(key,value==1);else Core.settings.put(key,value);});
    }
}
