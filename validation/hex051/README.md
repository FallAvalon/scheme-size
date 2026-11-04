# Проверки шестигранного меню 0.5.1

```powershell
./validation/hex051/Run.ps1 -DesktopJar work/audit/desktop-160.7-nosteam-test.jar -JdkHome 'C:/Program Files/JetBrains/PyCharm 2026.2.2/jbr' -GameJava 'C:/Program Files (x86)/Steam/steamapps/common/Mindustry/jre/bin/java.exe' -SmallWindow
```

Для 200% добавить `-UiScale 200 -Only plus` либо `-Only god`. Профили создаются в игнорируемых workspace-*, установленный профиль не используется. Ввод синтетический Arc/SDL, кадры получены из framebuffer.

Для отдельной визуальной сверки компонент HexSelection загружается из настоящего неизменённого релиза O279 по локальному пути `work/audit/o279/Scheme-Size-2.7.79.jar`. При отсутствии файла эталон пропускается с ORIGINAL_REFERENCE_SKIPPED; основной сценарий нашего меню выполняется. Референс не устанавливает оригинальный мод и не доказывает совместимость всех его функций с 160.7. JAR оригинала не входит ни в Git, ни в наши артефакты.

Логи и ограничения: [отчёт 22](../../reports/22_original_style_quick_menu.md). Старые полные проверки игровых действий: ../quick050 и ../fast042.
