package org.schemetools.host;

import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import java.util.List;
import java.util.Set;

/** Immutable filters and identity snapshots; no UI or custom network protocol. */
public record UnitScope(boolean allTeams,int teamId,boolean allTypes,Set<Integer> typeIds) {
    public UnitScope { typeIds=Set.copyOf(typeIds); }
    public static UnitScope all(){return new UnitScope(true,0,true,Set.of());}
    public void validate(){
        if(teamId<0||teamId>=Team.all.length)throw new IllegalArgumentException("st.error.input");
        for(int id:typeIds)if(id<0||id>=Vars.content.units().size)throw new IllegalArgumentException("st.error.input");
    }
    public boolean matches(Unit unit){return unit!=null&&!unit.dead&&unit.isAdded()&&(!allTeams?unit.team.id==teamId:true)&&(allTypes||typeIds.contains((int)unit.type.id));}
    public record Target(int id,Unit unit){
        public static Target capture(Unit unit){return new Target(unit.id,unit);}
        public boolean current(){return unit!=null&&unit.id==id&&Groups.unit.getByID(id)==unit&&unit.isAdded();}
    }
    public record Request(Object world,UnitScope scope,List<Target> targets){
        public Request { targets=List.copyOf(targets);if(scope==null)throw new IllegalArgumentException("st.error.input"); }
    }
}
