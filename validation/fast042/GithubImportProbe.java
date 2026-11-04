package probe;
import arc.*;import arc.util.*;import mindustry.*;
public class GithubImportProbe{
 static long started;static boolean reported;
 public static void main(String[]args){String repo=args[0],name=args[1];
 Events.on(mindustry.game.EventType.ClientLoadEvent.class,e->Core.app.post(()->{if(Vars.mods.list().size!=0){Log.err("SCHEMETOOLS_IMPORT_FAIL profile not empty");return;}started=System.currentTimeMillis();Vars.ui.mods.githubImportMod(repo,false,false);}));
 Events.run(mindustry.game.EventType.Trigger.update,()->{if(started==0||reported)return;var m=Vars.mods.list().find(mod->mod.name.equals(name));if(m!=null){reported=true;try{var z=new java.util.zip.ZipFile(m.file.file());if(z.getEntry(m.meta.main.replace('.','/')+".class")==null)throw new AssertionError("compiled main absent");z.close();String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(m.file.readBytes()));Log.info("SCHEMETOOLS_IMPORT_PASS repo=@ version=@ main=@ sha256=@",repo,m.meta.version,m.meta.main,sha);}catch(Throwable t){Log.err("SCHEMETOOLS_IMPORT_FAIL",t);}}else if(System.currentTimeMillis()-started>45000){reported=true;Log.err("SCHEMETOOLS_IMPORT_FAIL timeout repo=@",repo);}});
 mindustry.desktop.DesktopLauncher.main(new String[0]);}
}