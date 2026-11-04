# Проверки 0.5.0

Run.ps1 компилирует observer и запускает настоящий desktop 160.7 в отдельном workspace-* профиле. Не использует установленный профиль. Ввод синтетический через Arc/SDL; снимки — framebuffer игры. Полный результат и оговорки: ../../reports/21_held_menu_and_combat.md.

```powershell
./validation/quick050/Run.ps1 -DesktopJar work/audit/desktop-160.7-nosteam-test.jar -JdkHome 'C:/Program Files/JetBrains/PyCharm 2026.2.2/jbr' -GameJava 'C:/Program Files (x86)/Steam/steamapps/common/Mindustry/jre/bin/java.exe' -SmallWindow
```

Для 200% добавить `-UiScale 200 -Only god`. Штатный Steam JAR не изменять: тестовому экземпляру требуется отдельный non-Steam launcher. Скрипт проверяет build. Исходный upstream JAR хранится только в игнорируемом work/audit/o279, в наши сборки не включён.
