# Проверка полного предпросмотра 0.5.4

```powershell
./validation/preview054/Run.ps1 -DesktopJar work/audit/desktop-160.7-nosteam-test.jar -JdkHome 'C:/Program Files/JetBrains/PyCharm 2026.2.2/jbr' -GameJava 'C:/Program Files (x86)/Steam/steamapps/common/Mindustry/jre/bin/java.exe' -SmallWindow
```

Для масштаба 200% добавить `-UiScale 200 -Only plus` или `-Only god`. SDL-клиент и реальные игровые BuildPlan запускаются с синтетическим Arc-вводом в отдельных игнорируемых профилях. Проверяются исследованный/неисследованный/запрещённый блок, конфигурация, реальная очередь при пустом ядре, схема только с закрытым блоком и нестроительный юнит. Подробности и ограничения: [отчёт 25](../../reports/25_full_schematic_preview.md).
