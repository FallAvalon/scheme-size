# Регрессия назначения тегов 0.5.2

```powershell
./validation/tag052/Run.ps1 -DesktopJar work/audit/desktop-160.7-nosteam-test.jar -JdkHome 'C:/Program Files/JetBrains/PyCharm 2026.2.2/jbr' -GameJava 'C:/Program Files (x86)/Steam/steamapps/common/Mindustry/jre/bin/java.exe' -SmallWindow
```

Для 200%: `-UiScale 200 -Only plus` либо `-Only god`. Профили создаются в игнорируемых workspace-*, установленный профиль не используется. Ввод синтетический Arc/SDL.

regression-before-051.txt — настоящий отрицательный прогон до исправления. Новая последовательность: ЛКМ вниз, средняя вверх, ЛКМ вверх; после нескольких кадров диалог должен оставаться открытым и принимать обычный mouse click по тегу. Дополнительно проверены отпускание над кнопкой без клика, Escape без изменения, нажатие значка с удержанием и последующим отпусканием средней кнопки. Затем выполняются общие сценарии кольца/панели боя из hex051.

Результаты и границы проверки: [отчёт 23](../../reports/23_tag_assignment_fix.md). Скриншоты — framebuffer настоящей игры с тестовой библиотекой.
