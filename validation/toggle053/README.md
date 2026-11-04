# Проверка меню по нажатию 0.5.3

```powershell
./validation/toggle053/Run.ps1 -DesktopJar work/audit/desktop-160.7-nosteam-test.jar -JdkHome 'C:/Program Files/JetBrains/PyCharm 2026.2.2/jbr' -GameJava 'C:/Program Files (x86)/Steam/steamapps/common/Mindustry/jre/bin/java.exe' -SmallWindow
```

Для масштаба 200% добавить `-UiScale 200 -Only plus` либо `-Only god`. Тест запускает реальный SDL-клиент с синтетическим вводом в отдельных игнорируемых профилях. Результаты и ограничения: [отчёт 24](../../reports/24_click_quick_menu.md).
