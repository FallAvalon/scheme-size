# Текущие артефакты 0.3.1 — Size Plus / Size God

Изменены только названия, иконки и упаковка; полный паритет не заявлен. [Проверки и публикация](17_github_publication.md).

| Редакция | JAR | SHA-256 |
|---|---|---|
| S+ | [scheme-size-plus.jar](../mod/build/libs/scheme-size-plus.jar) | dc67d92620a766b985a0837cb6ba163696ec6640cc8880de113c4f90ccdd93d3 |
| SG | [scheme-size-god.jar](../mod/build/libs/scheme-size-god.jar) | da9f18de60e92956d8361284a87f977d915528f956b72978e6d6079bd87f61a9 |

[Совпадение испытанных JAR и повторной сборки](../validation/artifact-proof-031.txt). Ниже сохранён исторический отчёт 0.3.0; он не описывает новые иконки. Локальные build/-артефакты в Git не включаются, публичные JAR доступны в Releases соответствующих витрин.

---
# Исторический срез 0.3.0 — 04.11.2026

Актуальный срез: [Группы юнитов, иконка и публикация](16_roster_icon_publication.md). Оба JAR — общая кодовая база для Mindustry 160.7, версия 0.3.0. Полный O279, Linux, WAN и физический OS-ввод не объявлены проверенными.

| Редакция | Файл | SHA-256 |
|---|---|---|
| Plus | [scheme-size-plus.jar](../mod/build/publication/v0.3.0/scheme-size-plus/release/scheme-size-plus.jar) | 88dbbeae833cd37744d02bebbd72e47784364b8ab0836ee3b2eb84f9c099fe58 |
| God | [scheme-size-god.jar](../mod/build/publication/v0.3.0/scheme-size-god/release/scheme-size-god.jar) | 901715d129085941815a56797ef5cb34e38bedbb9e08acf95f2e57cbc4561741 |

[SHA256SUMS.txt](../mod/build/libs/SHA256SUMS.txt), [полная повторная сборка](../work/test-results/build-roster-icon-width-final-20261104-repeat.log) и [сверка](../work/test-results/reproducible-roster-icon-width-final-20261104.log) подтверждают идентичность на этом Windows/JDK. [Реально запущенные копии](../work/test-results/roster-icon-width-tested-artifact-hashes.log) совпадают с выдаваемыми JAR.

В [UI-проходе](../work/ui-probe/workspace-73fe508a0d214b5a9c14b50605f282ff/both/stdout.log) проверены отдельные редакции и совместная загрузка с одним God HUD. [headless](../work/headless-b6889e42052146929967dbe59494abaa/both/stdout.log), native смерть/защита игрока, параметры и точные границы runtime/сетевых проверок перечислены в [отчёте № 16](16_roster_icon_publication.md). ArtifactTest проверяет безопасные ID scheme-size-plus / scheme-size-god, Java 17, метаданные, одинаковый общий bytecode и границы содержимого.

Предыдущие срезы: [№ 15](15_unit_death_and_player_ui.md), [№ 14](14_ui_and_god_replacement.md), [№ 13](13_god_controls_and_ui.md), [№ 12](12_ui_compact_help.md), [№ 7](07_validation_and_remaining.md). Журналы промежуточных и неудачных прогонов сохранены.

Из каталога `mod` с полным JDK 17+ и неизменённым desktop.jar точно 160.7:

```powershell
./gradlew.bat check assemble '-PmindustryJar=C:\path\to\desktop.jar'
```

```sh
sh ./gradlew check assemble -PmindustryJar=/path/to/desktop.jar
```

Первому запуску Gradle wrapper может потребоваться загрузка Gradle 9.1.0. Команды и установка/удаление описаны в [русском руководстве](../mod/USER_GUIDE_RU.md); Linux-команда подготовлена, но в имеющейся Windows-среде не выполнялась. Проверки, включая настоящие игровые процессы, и открытые сценарии приведены в [отчёте № 7](07_validation_and_remaining.md), принадлежность и статус каждой функции — в [матрице](02_o279_implementation_matrix.md), внешние действия — в [плане № 6](06_external_test_plan.md).

[Готовые витрины 0.3.0](../mod/build/publication/v0.3.0/) и [инструкция публикации](../docs/schemesize/publishing.md). Публичные адреса/релизы не создавались.
