import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Offline storefront staging. No GitHub calls, credentials or game files. Run from mod/. */
public class PreparePublication {
 static String verifier(String file,String checksum,String main,String version){
  return """
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Standalone release integrity check; runtime implementation is in the shared source repository.
 * Run with JDK 17+: java VerifyRelease.java /path/to/__FILE__
 * This real Java utility also makes Mindustry 160.7 detect the storefront as a Java release repository.
 * No game libraries, downloaded code, credentials or installed profile are required.
 */
public final class VerifyRelease {
    static final String expectedSha256 = "__SHA__";
    static final String expectedMain = "__MAIN__";
    static final String expectedVersion = "__VERSION__";
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args.length == 0 ? "__FILE__" : args[0]);
        byte[] bytes = Files.readAllBytes(jar);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!expectedSha256.equals(actual)) throw new IllegalArgumentException("JAR SHA-256 mismatch: " + actual);
        try (var zip = new ZipFile(jar.toFile())) {
            if (zip.getEntry(expectedMain.replace('.', '/') + ".class") == null)
                throw new IllegalArgumentException("Compiled entry point missing");
            if (zip.getEntry("icon.png") == null || zip.getEntry("mod.hjson") == null)
                throw new IllegalArgumentException("Root icon/metadata missing");
            String metadata = new String(zip.getInputStream(zip.getEntry("mod.hjson")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (!metadata.contains("version: " + expectedVersion)) throw new IllegalArgumentException("Version mismatch");
        }
        System.out.println("PASS " + jar.getFileName() + " " + expectedVersion + " SHA-256 " + actual);
    }
}
""".replace("__FILE__",file).replace("__SHA__",checksum).replace("__MAIN__",main).replace("__VERSION__",version);
 }
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static String text(Path p)throws Exception{return Files.readString(p,StandardCharsets.UTF_8);}
 static void write(Path p,String value)throws Exception{Files.writeString(p,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
 static String value(String metadata,String key){for(String line:metadata.split("\\R"))if(line.startsWith(key+":"))return line.substring(key.length()+1).trim();throw new IllegalArgumentException("Missing metadata "+key);}
 public static void main(String[] args)throws Exception{
  Path project=Path.of("").toAbsolutePath().normalize();
  String version=value(text(project.resolve("editions/plus.hjson")),"version");
  Path output=args.length>0?Path.of(args[0]).toAbsolutePath().normalize():project.resolve("build/publication/v"+version);
  String source=args.length>1?args[1]:"";String revision=args.length>2?args[2]:"";
  if(!source.isEmpty()&&!source.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Expected source owner/repository");
  if(!revision.isEmpty()&&!revision.matches("[a-fA-F0-9]{40}"))throw new IllegalArgumentException("Expected full source commit SHA");
  if(Files.exists(output))throw new IllegalArgumentException("Output already exists; use a new directory to preserve previous staging: "+output);
  var metadata=new LinkedHashMap<String,String>();var artifacts=new LinkedHashMap<String,byte[]>();
  var icons=new LinkedHashMap<String,byte[]>();
  for(String edition:List.of("plus","god")){
   byte[] icon=Files.readAllBytes(project.resolve("editions/"+edition+"/icon.png"));icons.put(edition,icon);
   String meta=text(project.resolve("editions/"+edition+".hjson"));Path jar=project.resolve("build/libs/scheme-size-"+edition+".jar");
   if(!version.equals(value(meta,"version"))||!meta.contains("java: true")||meta.contains("hideBrowser: true"))throw new IllegalArgumentException("Incorrect storefront metadata: "+edition);
   try(var zip=new ZipFile(jar.toFile())){
    if(!meta.equals(new String(zip.getInputStream(zip.getEntry("mod.hjson")).readAllBytes(),StandardCharsets.UTF_8)))throw new IllegalArgumentException("JAR metadata differs; rebuild first");
    if(!Arrays.equals(icon,zip.getInputStream(zip.getEntry("icon.png")).readAllBytes()))throw new IllegalArgumentException("JAR icon differs; rebuild first");
   }
   metadata.put(edition,meta);artifacts.put(edition,Files.readAllBytes(jar));
  }
  var sources=new TreeSet<Path>();
  for(String dir:List.of("src/main","editions","tools"))try(var stream=Files.walk(project.resolve(dir))){stream.filter(Files::isRegularFile).forEach(sources::add);}
  sources.add(project.resolve("build.gradle"));sources.add(project.resolve("USER_GUIDE_RU.md"));
  var manifest=new StringBuilder();for(Path file:sources)manifest.append(sha(Files.readAllBytes(file))).append("  ").append(project.relativize(file).toString().replace('\\','/')).append('\n');
  Files.createDirectories(output);
  for(String edition:metadata.keySet()){
   byte[] icon=icons.get(edition);
   String name="scheme-size-"+edition;Path folder=output.resolve(name);Files.createDirectories(folder.resolve("release"));
   write(folder.resolve("mod.hjson"),metadata.get(edition));Files.write(folder.resolve("icon.png"),icon,StandardOpenOption.CREATE_NEW);
   String filename=name+".jar";Files.write(folder.resolve("release/"+filename),artifacts.get(edition),StandardOpenOption.CREATE_NEW);
   write(folder.resolve("release/SHA256SUMS.txt"),sha(artifacts.get(edition))+"  "+filename+"\n");
   String title=edition.equals("god")?"Size God (SG)":"Size Plus (S+)";
   String sourceLink=source.isEmpty()?"URL исходного репозитория указывается владельцем при публикации.":"Исходники: https://github.com/"+source+(revision.isEmpty()?"":"/tree/"+revision);
   String sourceBase=source.isEmpty()?"":"https://github.com/"+source+(revision.isEmpty()?"/blob/main":"/blob/"+revision);
   String features=edition.equals("god")?"Все инструменты Plus и управление матчем: игроки, команды, ресурсы, юниты, правила, арена, события и «Босс-игрок».":"Схемы и быстрый доступ, импорт изображений, строительные инструменты, визуальные слои и локальный прогноз маршрутов противников по клеткам.";
   write(folder.resolve("README.md"),"# "+title+"\n\n<img src=\"icon.png\" width=\"128\" height=\"128\" alt=\""+title+"\">\n\n"+features+"\n\n**[Скачать JAR](https://github.com/FallAvalon/"+name+"/releases/latest)** · [Руководство](USER_GUIDE_RU.md) · [Сообщить об ошибке](https://github.com/FallAvalon/scheme-size/issues)\n\n## Совместимость\n\nMindustry **V8 Build 160.7**, настольные Windows/Linux. Windows проверен, Linux пока не проверен. Android не поддерживается.\n\n**Тестовая версия "+version+".** Полный паритет с Scheme-Size 2.7.79 не подтверждён. Основной сетевой сценарий — мод у встроенного хоста, гости без модов; проверенные действия и ограничения перечислены в отчётах. WAN пока не проверен.\n\n## Установка\n\n1. Скачайте `"+filename+"` из Releases.\n2. Модификации → Импортировать модификацию → выберите JAR.\n3. Перезапустите игру. Инструменты — на панели S+/SG слева; справка — кнопки ?.\n\nОбычно достаточно одной редакции: God включает общие инструменты Plus. Начните с отдельного тестового мира. Импорт без браузера каталога: Модификации → Импортировать модификацию → Из GitHub → `FallAvalon/"+name+"`. Витрина распознаётся как Java благодаря VerifyRelease.java; игра скачивает JAR из последнего релиза. Программа VerifyRelease также проверяет контрольную сумму JAR без библиотек игры.\n\n## Интерфейс\n\n![Интерфейс 0.4.0, сохранён в "+version+"](screenshot.png)\n\nЮниты → На карте → команда → типы по иконкам → красная кнопка с названием и количеством целей. Можно выбрать несколько типов, рамку или отдельных юнитов. Управляемые игроками юниты исключаются повторно при исполнении. Native смерть может вызвать побочный урон.\n\n## Исходники и проверки\n\nЭто витрина сборок из общей кодовой базы. "+sourceLink+"\n\n[Матрица реализации]("+sourceBase+"/reports/02_o279_implementation_matrix.md) · [Проверки "+version+"]("+sourceBase+"/reports/20_fast_map_controls.md) · [История сетевых проверок]("+sourceBase+"/reports/16_roster_icon_publication.md).\n\nИсходный коммит и хеш манифеста указаны в source.json; к релизу приложен SHA256SUMS.txt. Это самостоятельная реализация, не официальный выпуск оригинального Scheme-Size.\n");
   Path screenshot=project.resolve("../reports/screenshots/ui18-"+edition+"-roster.png");
   if(Files.isRegularFile(screenshot))Files.copy(screenshot,folder.resolve("screenshot.png"));
   write(folder.resolve("USER_GUIDE_RU.md"),"# "+title+": короткое руководство\n\nИмпортируйте JAR из Releases в Mindustry 160.7 и перезапустите игру. Обычно достаточно одной редакции. God включает общие функции Plus и инструменты организатора/босса.\n\nЮниты → На карте → выбрать команду → отметить типы по иконкам → Убить подходящих. Рамка и раскрываемый список позволяют выбрать отдельных юнитов. Снятие последнего типа даёт пустой фильтр. Юниты под управлением игроков исключаются повторно при исполнении. Смерть вызывает настоящие взрывы/падение; отмены нет и защита не даёт иммунитета от побочного урона.\n\nAlt + боковая кнопка мыши «Назад» (Mouse Back) переносит своего игрока под курсор, включая подключение к серверу (сервер может исправить позицию). Срабатывает над картой без открытого диалога или ввода текста. Настройки → Клавиши → Телепорт под курсор позволяют переназначить/отключить сочетание.\n\nСоздание юнитов находится во вкладке Создать, параметры сохраняются. Пояснения открываются по ?. Изменения мира доступны только хосту.\n\nУдаление: отключить или удалить мод в штатном меню и перезапустить игру. Пользовательские сохранения мод не удаляет.\n\nОшибки: укажите редакцию, версию, SHA-256 JAR, разрешение, масштаб и шаги. Лог Windows: %APPDATA%/Mindustry/last_log.txt; Linux: ~/.local/share/Mindustry/last_log.txt (при изменённом каталоге данных путь иной).\n\nПроверки Linux, WAN и физического OS-ввода не заявлены. Полный паритет O279 не заявлен.\n");
   write(folder.resolve("RELEASE_NOTES.md"),"# "+title+" "+version+" [v160.7]\n\nТестовый выпуск для настольной Mindustry **160.7**.\n\n"+features+"\n\n- Колесо над картой меняет зум при открытых панелях и окнах выбора мода; над списками прокручивает список.\n- Частые действия без подтверждений по умолчанию; переключатель возвращает подтверждения.\n- God: прямой выбор Пол / Руда / Стены, природные стены карты, применение и отмена.\n- Alt + Mouse Back также на клиенте сервера через обычный snapshot; strict/античит может исправить позицию.\n- Подписанные разделы, сворачивание подписей и компактные панели.\n- Карточки схем с превью, вкладки Библиотека / С карты.\n- Карточки юнитов, конкретная кнопка убийства группы, шаги создания и предпросмотр точки на карте. Исправлена отмена правой кнопкой.\n\nУстановка: импортировать `"+filename+"` через меню модификаций и перезапустить игру. Обычно нужна только одна редакция.\n\nСборка/check, упаковка, отдельные редакции и совместная загрузка проверены на Windows. Полный отчёт: "+sourceBase+"/reports/20_fast_map_controls.md\n\nLinux, WAN и физический OS-ввод не проверены. Полный паритет O279 не заявлен. Android не поддерживается. Смерть юнитов необратима и может вызвать побочный урон.\n\nВложения: **"+filename+"** и **SHA256SUMS.txt**. "+sourceLink+"\n");
   write(folder.resolve("source-manifest.sha256"),manifest.toString());
   write(folder.resolve("VerifyRelease.java"),verifier(filename,sha(artifacts.get(edition)),value(metadata.get(edition),"main"),version));
   write(folder.resolve("source.json"),"{\n  \"repository\": \""+source+"\",\n  \"revision\": \""+revision+"\",\n  \"manifestSha256\": \""+sha(manifest.toString().getBytes(StandardCharsets.UTF_8))+"\"\n}\n");
   write(folder.resolve(".gitignore"),"/release/\n");
   System.out.println("Prepared "+folder+"; one JAR: "+sha(artifacts.get(edition)));
  }
 }
}
