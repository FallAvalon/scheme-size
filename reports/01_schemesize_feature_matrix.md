# Scheme-Size: сравнительная матрица функций

Повторная проверка 01.11.2026. **Исходное дерево релиза 2.7.79 инвентаризировано полностью, но матрица не является подтверждением поведенческого паритета.** Просмотрены 52 Java-файла и 174 ключа основного bundle. В изолированной копии Mindustry 160.7 на Windows загрузчик обнаружил мод, приложение дошло до последующих сообщений загрузки; UI, мир и функции не проверялись. Сетевой сценарий не проходился. Linux-среды нет. Установленный ZIP совпадает с тегом по метаданным и проверенным ресурсам, но скомпилированные классы побайтово не сверены. Подробности в [отчёте](01_discovery_report.md).

## Как читать

- **U3** — текущий [upstream main `f6c50f484ad690bef73c26a8300001b4e75b2ab2`](https://github.com/xzxADIxzx/Scheme-Size/tree/f6c50f484ad690bef73c26a8300001b4e75b2ab2), метаданные 3.0.0, вход `schema.Main`. Строки `schema/` относятся к активному entrypoint, но работоспособность не проверена.
- **O279** — официальный релизный тег [`V2.7.79`](https://github.com/xzxADIxzx/Scheme-Size/tree/7936af9a36a46d752c9e5e3d73ebec2b48ca910b), точный исходный commit `7936af9a36a46d752c9e5e3d73ebec2b48ca910b`. Новый раздел ниже перечисляет проверенные по этому дереву функции и уточняет старые строки UL.
- **UL** — старое дерево `scheme/` **в том же commit**. Наличие методов в дереве не доказывает их вызов текущим `schema.Main`, и оно не отождествлено с установленным бинарником 2.7.79. Сравнение Git blob с релизным тегом выявило различия в 30 из 53 проверенных файлов старого дерева и метаданных. В таблице UL означает «найден код в main», а не «функция текущего U3 или релизного 2.7.79 работает».
- **P** — независимый [порт `9327f72eed19ef957bb9b3e74ea03120cb7dd8e1`](https://github.com/ItsXeroc/Scheme-Size/tree/9327f72eed19ef957bb9b3e74ea03120cb7dd8e1), `scheme.Main`, metadata 2.0.0. Код найден — не подтверждение работоспособности на 160.7. `?` означает, что эквивалент или поведение не установлены; `TODO` — явная заглушка, не работающая функция.
- **К** — локальный клиент/интерфейс; **Х** — локальная игра или авторитетный встроенный хост; **С** — отдельная серверная часть; **К→С** — запрос с проверкой на сервере. В старых таблицах UL/P наличие кода не доказывает достижимость или сетевую авторитетность. Игра — зависимости Mindustry/Arc соответствующей версии и обычного контента; собственные настройки не всегда сохраняются.
- По строкам «код» означает только подтверждение прочитанным кодом, ни одна строка не имеет статуса «подтверждено тестом». «Паритет» здесь — кандидат на обязательный аудит оригинала, а не принятое доказательство пользовательского сценария.

Ссылки в таблице закреплены на commit: [U3 schema](https://github.com/xzxADIxzx/Scheme-Size/tree/f6c50f484ad690bef73c26a8300001b4e75b2ab2/src/java/schema), [UL scheme](https://github.com/xzxADIxzx/Scheme-Size/tree/f6c50f484ad690bef73c26a8300001b4e75b2ab2/src/java/scheme), [P scheme](https://github.com/ItsXeroc/Scheme-Size/tree/9327f72eed19ef957bb9b3e74ea03120cb7dd8e1/src/java/scheme). Пути в ячейках относительно `src/java/`; номер после `:` — начальная строка в зафиксированном файле. Для просмотра используйте базовый URL соответствующего дерева и путь; например [U3 schema/Main.java:60](https://github.com/xzxADIxzx/Scheme-Size/blob/f6c50f484ad690bef73c26a8300001b4e75b2ab2/src/java/schema/Main.java#L60).

Общие ограничения: инструменты ввода и интерфейс зависят от desktop UI и карты, построительные действия могут отправлять штатные запросы и подчиняются правам сервера; административные действия не становятся разрешёнными от наличия кнопки. Если сохранение отдельно не указано, оно **не выяснено**. Windows-проверка подтверждает только прохождение этапа обнаружения мода загрузчиком и продолжение загрузки приложения, не вызов каждого модуля или поведение функций; Linux, UI и сеть не проверялись. У портовых дополнений статус — «оценить отдельно», а не автоматическое требование.

## Управление и схемы

| Функция и сценарий | Источник и место в коде | Оригинал | Порт | Зависимость Mindustry | Клиент/хост/сервер | Windows/Linux | Решение/статус |
|---|---|---|---|---|---|---|---|
| Лимит выбора схемы 128 | U3 `schema/Main.java:60` | U3 присваивает 128 | P назначает 512 | `Vars.maxSchematicSize`, формат и серверные пределы | К; применение планов зависит от С | не тест | код; практический предел не выяснен |
| Лимит схемы 512 | UL `scheme/Main.java:29`, P `scheme/Main.java:25`; локальный архив 2.7.79 `mod.hjson` | UL код; 2.7.79 заявлено в метаданных | P код | как выше | К/С | не тест | важный кандидат паритета, проверить считывание и сервер |
| Сохранить схему из области | UL/P `scheme/moded/ModedSchematics.java` `create` | UL | P | `Schematics`, выбор клеток | К; мир читается локально | не тест | код; версии формата и данные не выяснены |
| Слой зданий при копировании | UL/P `ModedSchematics.java` `Layer.building` | UL | P | `Schematics.create` | К | не тест | код |
| Скопировать слой пола | UL/P `ModedSchematics.java` `Layer.floor` | UL | P | `Tile.floor` | К; вставка особая | не тест | код; сценарий вставки исследовать |
| Скопировать слой препятствий | UL/P `ModedSchematics.java` `Layer.block` | UL | P | `Tile.block` | К | не тест | код |
| Скопировать overlay/руду | UL/P `ModedSchematics.java` `Layer.overlay` | UL | P | `Tile.overlay` | К | не тест | код |
| Скопировать terrain вместе с несколькими слоями | UL/P `ModedSchematics.java` `createTerrain` | UL | P | формат схем | К; мир/права при вставке | не тест | код; сохранение и совместимость не выяснены |
| Переключение слоя схемы | UL/P `ModedSchematics.java` `nextLayer`, `HudFragment.java` | UL | P | UI/input | К | не тест | код; точные клавиши UL уточнить |
| Импорт и восстановление схем | UL/P `scheme/ui/dialogs/SchemasDialog.java` и `ModedSchematics.java` `read` | UL | P | чтение msch/base64, контент | К | не тест | код; лимиты, повреждённые файлы и побочные изменения надо проверить |
| Переключить и выбрать схему по тегу | U3 `schema/input/DesktopInput.java:349`, `schema/ui/polygon/Polygon.java`; UL/P `ShortcutFragment.java` | U3 вызывает меню, содержимое poly-схемы не доказано; UL меню | P | tags/settings | К | не тест | неполно: U3 и UL/P сравнить по UI; tag UL/P сохраняется в settings |
| Открыть стандартную библиотеку схем | U3 `schema/input/DesktopInput.java:349` | U3 вызывает `ui.schematics.show()` | P соответствие ? | UI игры | К | не тест | код |
| Отразить схему по X | U3 `schema/input/Keybind.java:39` | биндинг объявлен; активный обработчик не выяснен | P ? | стандартные планы схем | К | не тест | не выяснено: назначение не доказывает исполнение |
| Отразить схему по Y | U3 `schema/input/Keybind.java:40` | биндинг объявлен | P ? | планы схем | К | не тест | не выяснено |
| Создать схему из выделенной области | U3 `schema/input/Keybind.java:38` | объявлено | UL/P `ModedSchematics.create` | схемы/ввод | К | не тест | U3 обработчик не выяснен |
| Изменить сочетания, модификаторы и оси | U3 `schema/input/Keybind.java:151`, `schema/ui/dialogs/KeybindDialog.java:25` | U3 код | P `scheme/input/SBinding.java` регистрирует собственные binds | Arc `KeyBind`, settings | К | не тест | настройки `schema-keybind-*`; сравнить UX и дефолты |
| Сбросить одну клавишу | U3 `schema/ui/dialogs/KeybindDialog.java:46` | U3 код | P ? | settings | К | не тест | код |
| Очистить назначение клавиши | U3 `schema/ui/dialogs/KeybindDialog.java:47` | U3 код | P ? | input | К | не тест | код; проверка постоянства нужна |
| Сбросить все клавиши | U3 `schema/ui/dialogs/KeybindDialog.java:49` | U3 код | P ? | settings | К | не тест | код |
| Разрешить конфликт модификаторов | U3 `schema/input/Keybind.java:213` и `KeybindDialog.java:133` | U3 код | P ? | KeyCode/settings | К | не тест | код; не заявлять полное обнаружение конфликтов с модами |
| Перемещение, камера и прицел к мыши | U3 `schema/input/DesktopInput.java:96` | U3 код | UL/P `ModedDesktopInput.java` частично | input/camera | К; движение сервер авторитетен | не тест | проверить на конфликт с вводом игры |
| Респавн и груз | U3 `schema/input/DesktopInput.java:139` | U3 использует `Call.unitClear`, payload requests | P ? | ванильные RPC/права | К→С | не тест | код; обычные права |
| Телепорт собственного юнита | U3 `schema/input/DesktopInput.java:123`; UL/P `admins/Internal.java` | U3 локальный `unit.set`, не доказана сетевая авторитетность; UL есть админ-путь | P есть админ-путь | положение юнита, сервер | К/Х | не тест | не обещать телепорт на чужом сервере |
| Удалить своего юнита отдельной клавишей | U3 `schema/input/DesktopInput.java:140` | **TODO**, пустой оператор | UL/P админ-методы присутствуют | unit/права | Х/С | не тест | U3 не считать готовым |
| Выбрать блок радиальным меню | U3 `schema/ui/polygon/BlockPolygon.java:41` | U3 код | P ? | content/category, ограничения окружения | К | не тест | код |
| Поиск блока отдельной клавишей | U3 `schema/input/DesktopInput.java:307` | **TODO** | P ? | UI | К | не тест | не выяснено |
| Копировать блок под курсором | U3 `schema/input/DesktopInput.java:315`; UL/P `BuildingTools.java` | U3 код; UL изучить | P код | tiles/plans | К | не тест | код; конфигурация и ограничения не выяснены |
| Поворот выбранного/построенного блока | U3 `schema/input/Rotatable.java:41` | U3 код | P ? | `Call.rotateBlock` | К→С | не тест | код; проверка прав сервера |
| Пауза очереди строительства | U3 `schema/input/DesktopInput.java:309` | U3 код | P ? | планы | К | не тест | код; состояние персистентности не выяснено |
| Очистить очередь планов | U3 `schema/input/DesktopInput.java:310` | U3 код | P ? | планы | К | не тест | код |
| Выбрать все юниты | U3 `schema/input/DesktopInput.java:208` | U3 код | P ? | команды | К→С | не тест | код; ограничения команды |
| Выбрать все фабрики | U3 `schema/input/DesktopInput.java:214` | U3 код | P ? | factory commands | К→С | не тест | код |
| Приказ перемещения/атаки | U3 `schema/input/DesktopInput.java:225` | U3 `Call.commandUnits` | P ? | штатный протокол | К→С | не тест | код, доступ зависит от правил и сервера |

## Строительство и среда

Большинство строк ниже существует в старом `scheme/`, но **достижимость из U3 не доказана**. Названия инструментов и сценарии подтверждаются [UL BuildingTools](https://github.com/xzxADIxzx/Scheme-Size/blob/f6c50f484ad690bef73c26a8300001b4e75b2ab2/src/java/scheme/tools/BuildingTools.java#L53) и [описаниями инструментов](https://github.com/xzxADIxzx/Scheme-Size/blob/f6c50f484ad690bef73c26a8300001b4e75b2ab2/src/resources/bundles/bundle.properties#L194); кнопки надо дополнительно пройти в исходной игре.

| Функция и сценарий | Источник и место в коде | Оригинал | Порт | Зависимость Mindustry | Клиент/хост/сервер | Windows/Linux | Решение/статус |
|---|---|---|---|---|---|---|---|
| Взять/сбросить предмет из здания | UL/P `scheme/tools/BuildingTools.java` `drop` | UL; U3 `drop` TODO | P | inventory/requestItem | К→С | не тест | код; настройки и пределы проверить |
| Заменить выбранное и соседние здания | UL/P `BuildingTools.java` `replace` | UL | P | BuildPlan/валидность | К→С | не тест | код; размер области уточнить |
| Удалить выбранное и соседние здания | UL/P `BuildingTools.java` `remove` | UL | P | планы демонтажа | К→С | не тест | код |
| Вернуть последние удалённые планы | UL/P `BuildingTools.java` `removed`, HUD `flushLastRemoved` | UL | P | планы/конфигурация | К→С | не тест | код; после перезапуска не выяснено |
| Подключить энергоузлы к ближайшему источнику | UL/P `BuildingTools.java` `connect` | UL | P | power nodes/config | К→С | не тест | код; обработать сетевые права |
| Заполнить область блоком | UL/P `BuildingTools.java` `fill` | UL | P | BuildPlan / выделение | К→С | не тест | код; пределы уточнить |
| Построить квадрат | UL/P `BuildingTools.java` `square` | UL | P | BuildPlan | К→С | не тест | код |
| Построить круг | UL/P `BuildingTools.java` `circle` | UL | P | BuildPlan | К→С | не тест | код |
| Настроить размер инструмента | UL/P `BuildingTools.java` `resize`, `HudFragment.java` | UL | P | UI | К | не тест | код; персистентность не выяснена |
| Заливка слоёв карты в админ-режиме | UL/P `admins/Internal.java` `fill`, `TileSelectDialog.java` | UL | P | Tile/Floor/Block | Х или С | не тест | код; ванильное сетевое применение проверить отдельно |
| Кисть карты в админ-режиме | UL/P `admins/Internal.java` `brush` | UL | P | карта, серверные операции | Х или С | не тест | код; лимиты и синхронизация не проверены |
| Конвертация картинки для логического дисплея | UL/P `scheme/tools/ImageParser.java`, `ui/dialogs/ImageParserDialog.java` | UL | P | изображение/процессоры/дисплей | К/Х | не тест | код; форматы, ограничения и размер результата не выяснены |

## Информация, отображение и интерфейс

| Функция и сценарий | Источник и место в коде | Оригинал | Порт | Зависимость Mindustry | Клиент/хост/сервер | Windows/Linux | Решение/статус |
|---|---|---|---|---|---|---|---|
| Предметы ядра с выбором команды | U3 `schema/ui/hud/CoreInfo.java`; UL/P `scheme/ui/CoreInfoFragment.java` | U3 и UL | P | `Team.core`, `ItemModule` | К | не тест | код; данные иной команды могут быть недоступны клиенту |
| Изменение количества предметов ядра за время | U3 `schema/ui/hud/CoreInfo.java` `WindowedMean` | U3 | UL/P `CoreInfoFragment` проверить точное соответствие | снимки инвентаря | К | не тест | код, точность при сетевых задержках не выяснена |
| Баланс и запас электрической сети | U3 `schema/ui/hud/CoreInfo.java` `balance`, `stored` | U3 | UL/P `PowerBars` | power graph | К | не тест | код; клиент видит доступный граф |
| Выбор отображаемой сети питания | U3 `schema/ui/hud/CoreInfo.java` `chooseGrid` | U3 | UL/P `CoreInfoFragment` | graph | К | не тест | код |
| Рентген наложением | U3 `schema/tools/Overlay.java:30`, `drawXray`; UL/P `RendererConfigDialog` | U3/UL | P | renderer | К | не тест | код |
| Полосы здоровья зданий | U3 `schema/tools/Overlay.java:33` | U3 | P ? | renderer | К | не тест | код; настройка `blockhealth` |
| Переключение видимости HUD | U3 `schema/input/DesktopInput.java:290` | U3 | P ? | HUD | К | не тест | код |
| Лазеры энергосети | U3 `schema/input/DesktopInput.java:291`; UL/P `RendererConfigDialog.java:23` | U3/UL | P | `lasersopacity` | К | не тест | код, settings |
| Значки состояния блоков | U3 `schema/input/DesktopInput.java:299`; UL/P `RendererConfigDialog.java:24` | U3/UL | P | `blockstatus` | К | не тест | код, settings |
| Свет | UL/P `RendererConfigDialog.java:25` | UL | P | renderer/state | К | не тест | код |
| Темнота | UL/P `RendererConfigDialog.java:26` | UL | P | renderer | К | не тест | код |
| Туман | UL/P `RendererConfigDialog.java:27` | UL | P | `state.rules.fog` | К/Х | не тест | код; это изменение правил, а не гарантированно чистый локальный слой |
| Скрыть юниты | UL/P `RendererConfigDialog.java:34` | UL | P | renderer | К | не тест | код |
| Сетка | UL/P `RendererConfigDialog.java:35` | UL | P | renderer | К | не тест | код |
| Линейка | UL/P `RendererConfigDialog.java:36` | UL | P | renderer | К | не тест | код |
| Сведения о юнитах на поле | UL/P `RendererConfigDialog.java:37` | UL | P | renderer/entities | К | не тест | код |
| Убрать границы отображения | UL/P `RendererConfigDialog.java:38` | UL | P | renderer | К | не тест | код; точный эффект проверить |
| Радиус юнита | UL/P `RendererConfigDialog.java:42` | UL | P | renderer | К | не тест | код |
| Радиус турели | UL/P `RendererConfigDialog.java:43` | UL | P | renderer/content | К | не тест | код |
| Радиус реактора | UL/P `RendererConfigDialog.java:44` | UL | P | renderer/content | К | не тест | код |
| Радиус ускорителя | UL/P `RendererConfigDialog.java:45` | UL | P | renderer/content | К | не тест | код |
| Увеличенная карта, панорамирование и масштаб | U3 `schema/ui/fragments/MapFragment.java:35` | U3 | UL/P ? | minimap/camera | К | не тест | код; проверить UI scale |
| Радиус взрыва выбранного здания | U3 `schema/ui/fragments/ConfigFragment.java:140` | U3 | P ? | Building | К | не тест | код |
| Подсветка руд у бура | U3 `schema/ui/fragments/ConfigFragment.java:148` | U3 | P ? | DrillBuild | К | не тест | код |
| Цепочка мостов | U3 `schema/ui/fragments/ConfigFragment.java:166` | U3 | P ? | ItemBridgeBuild | К | не тест | код |
| Информация о следующей волне и копирование | UL/P `scheme/ui/dialogs/WaveApproachingDialog.java` | UL | P | правила волн/content | К | не тест | код; сохранение и точность проверить |
| Информация об управляемом юните и волне в U3 | U3 `schema/ui/hud/UnitInfo.java`, `WaveInfo.java` | файлы пустые/неполные | P отдельный UI | HUD | К | не тест | не считать заголовок HudFragment доказательством реализации |

## Организатор, AI, консоль и сеть

Для UL/P приведено место кода, а не гарантия авторитетности. Реальная доступность зависит от реализации `Internal`, `SlashJs`, сервера и прав. Портовый серверный протокол — отдельное расширение.

| Функция и сценарий | Источник и место в коде | Оригинал | Порт | Зависимость Mindustry | Клиент/хост/сервер | Windows/Linux | Решение/статус |
|---|---|---|---|---|---|---|---|
| Выбрать тип и создать юнитов | UL/P `scheme/tools/admins/Internal.java` `spawnUnits` | UL | P | unit/content/cap | Х; К→С с сервером | не тест | код; скорость, лимит и сеть проверить |
| Управлять/очистить выбранного юнита | UL/P `admins/Internal.java` `manageUnit`, `despawn` | UL | P | Unit | Х/С | не тест | код; назначения не выяснены |
| Применить/убрать статус | UL/P `admins/Internal.java` `manageEffect`, `ContentSelectDialog` | UL | P | StatusEffect | Х/С | не тест | код; синхронизация не проверена |
| Изменить предметы ядра | UL/P `admins/Internal.java` `manageItem` | UL | P | ItemModule/core | Х/С | не тест | код; объём и сеть проверить |
| Сменить команду | UL/P `admins/Internal.java` `manageTeam` | UL | P | Player/Team | Х/С | не тест | код; права и респавн |
| Радужная команда | UL/P `scheme/tools/RainbowTeam.java` | UL | P | team updates | Х/С | не тест | код; смысл и риски уточнить |
| Поставить ядро | UL/P `admins/Internal.java` `placeCore` | UL | P | block/tile | Х/С | не тест | код; сетевые последствия |
| Телепорт | UL/P `admins/Internal.java` `teleport` | UL | P | player/unit | Х/С | не тест | код; исправление позиции клиента проверить |
| Kick, ban, trace, смена команды игрока | UL/P `scheme/ui/PlayerListFragment.java` | UL | P | `Call.adminRequest` | К→С | не тест | код; штатные права сервера |
| Управление правилами в UI | P `scheme/ui/dialogs/RuleSetterDialog.java`, `admins/Internal.java:32` | UL: отдельный эквивалент не выяснен | P | `Rules`, `Call.setRules` | Х/С | не тест | дополнение порта; отдельное решение |
| Серверные пакеты админских инструментов | P `scheme/ServerSide.java:42`, `admins/SchemeNet.java` | UL: не доказано | P | серверная установка и зарегистрированные handlers | К→С | не тест | дополнение порта; проверка admin есть, полноту валидации не аудировали |
| Клиентский AI добычи | UL/P `scheme/ai/NetMinerAI.java` | UL | P | управление юнитом / сеть | К→С | не тест | код; внешние ограничения сервера и UX |
| Gamma AI | UL/P `scheme/ai/GammaAI.java`, `AISelectDialog.java` | UL | P | ввод, юнит, сервер | К→С | не тест | исходные режимы перечислены в инвентаре O279 ниже; фактическая работа не тестировалась |
| Перейти к игроку AI | UL/P `scheme/ui/dialogs/AISelectDialog.java` `gotoppl` | UL | P | Player/AI | К→С | не тест | код |
| Улучшенная консоль/константы скрипта | U3 `schema/Main.java:98`; UL `scheme/ui/ConsoleFragment.java`, P `scheme/Main.java` | U3 добавляет скрипт; UL отдельный UI | P внедряет константы; эквивалент UI не выяснен | scripts/Rhino | К | не тест | код; функции истории/планировщика и сохранение требуют аудита |
| Проверка обновлений | U3 `schema/Updater.java:21`; UL/P `SchemeUpdater` | U3/UL | P | интернет/GitHub | К | не тест | код; поведение сети и согласие пользователя обсудить |
| Copy Link and Join | UL `scheme/ClajIntegration.java`, `ui/dialogs/JoinViaClajDialog.java`, `ManageRoomsDialog.java` | UL; U3 `clajURLs` не доказывает полную интеграцию | P содержит встроенный CLaJ client | внешний сервис/прокси/протокол | К + удалённый сервис | не тест | код; доступность сервиса, лицензии и совместимость выяснить |
| Настройка управления и параметров | O279 `scheme/ui/dialogs/SettingsMenuDialog.java`, UL/P одноимённые диалоги | O279/UL | P | Core.settings и клавишные бинды | К | не тест | параметры O279 перечислены ниже; реальные конфликты биндов не тестировались |

## Статус пунктов первоначального отчёта

| Пункт | Итог после повторного аудита |
|---|---|
| Релизный baseline и исходный код | Все 52 Java-файла O279 инвентаризированы, ключевые модули подробно просмотрены; метаданные и ресурсы установленного ZIP сверены с тегом. Побайтовое сравнение классов и ручное прохождение интерфейса остаются открытыми. |
| Бинды и действия | Основные бинды сопоставлены с обработчиками в O279. Конфликты с vanilla/V8, все контексты срабатывания и сохранение настроек не тестировались. |
| Функции и ограничения | Функции перечислены по отдельным операциям в матрице; наличие кода не подтверждает выполнение в игре. Неясные сценарии отмечены как непроверенные. |
| Порт и лицензии | Порт сравнён отдельно; CLaJ/серверные добавления не приписаны upstream. Условия повторного использования кода и ассетов upstream не установлены. |
| Windows/Linux и сеть | Windows: изолированный запуск приложения и обнаружение мода загрузчиком. Linux, ручные действия, vanilla-клиент, переподключение и dedicated не тестировались. |

### Дополнительная точечная сверка релизного тега 2.7.79

Чтение файлов тега `7936af9a36a46d752c9e5e3d73ebec2b48ca910b` подтверждает наличие в релизных исходниках: лимита 512 ([Main:31](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Main.java#L31)); методов выдачи предмета, замены/удаления соседних блоков, подключения узлов, заливки, квадрата и круга ([BuildingTools:53](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java#L53)); административных методов юнитов, эффектов, предметов, команд, ядра, телепорта и кисти ([Internal:22](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/admins/Internal.java#L22)); слоёв схем ([ModedSchematics](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java#L190)); кнопок размера, возвращения планов и переключения слоя ([HudFragment](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/HudFragment.java#L145)). Это точечное подтверждение, не означает, что каждое поведение в строках UL одинаково в обеих версиях.

В [ModedBinding:15](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedBinding.java#L15) релиз задаёт отдельные сочетания по умолчанию: F12 настройки админ-инструментов; Y настройки отображения; G быстрый выбор схем; F7 панель ресурсов; I диалог AI; F2 юниты; F3 эффекты; F4 предметы; `;` команда; `'` ядро; левый Alt — модификатор. Сочетания регистрируются через прежний `arc.KeyBinds`, поэтому на V8 требуют адаптации и проверки конфликтов. Связь бинда с фактическим действием, режимами и сохранением нужно дополнительно проверить для каждого; сам enum не доказывает корректной работы. Порт регистрирует другие новые бинды с `unset` в [SBinding](https://github.com/ItsXeroc/Scheme-Size/blob/9327f72eed19ef957bb9b3e74ea03120cb7dd8e1/src/java/scheme/input/SBinding.java#L6).

Подробная точечная сверка релиза и биндов приведена в следующих разделах. Ручные проверки UI и клавиш, реальные сценарии vanilla-клиента, Linux и побайтовая сверка JAR остались открытыми; эти ограничения перечислены также в итоговом разделе.

## Полный исходный инвентарь официального релиза 2.7.79

Ниже `O279` означает исходники точного официального тега. Наличие поведения подтверждено чтением кода; отдельной колонкой отмечен незакрытый практический тест. Ссылка на корень исходников закреплена на commit [7936af9a36a46d752c9e5e3d73ebec2b48ca910b](https://github.com/xzxADIxzx/Scheme-Size/tree/7936af9a36a46d752c9e5e3d73ebec2b48ca910b).

| Отдельная функция | Источник O279 | Что делает код | Проверка поведения |
|---|---|---|---|
| Размер схемы до 512 | [`Main.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Main.java) | Задаёт `maxSchematicSize=512`; распознаёт схемы больше штатного лимита и при необходимости переименовывает `.msch` в `.mtls`. | Только запуск мода; чтение, сохранение, вставка и предел обычного сервера не проверены. |
| Слой схемы зданий | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java) | Штатное копирование зданий и планы размещения. | Не проходилось в игре. |
| Слой схемы пола | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java) | Копирует пол; применение проходит через админ-режим редактирования карты. | Не проходилось; сетевой путь не проверен. |
| Слой блоков-препятствий | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java) | Копирует блоки карты, для которых нет здания. | Не проходилось. |
| Слой overlay | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java) | Копирует руду/overlay. | Не проходилось. |
| Слой terrain | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java) | Собирает пол, overlay и не-зданийные блоки в одну схему; слой меняется сочетанием Alt + штатный выбор схемы. | Не проходилось; формат/границы проверены только по коду. |
| Импорт из файла и Base64 | [`ModedSchematics.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedSchematics.java), [`SchemasDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/SchemasDialog.java) | Читает форматы схем, теги и категории; есть импорт из буфера и файловый диалог. | Не проходилось; повреждённые и большие файлы не проверены. |
| Импорт картинки как схемы | [`ImageParser.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/ImageParser.java), [`ImageParserDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/ImageParserDialog.java) | Настройки фильтрации/разбиения изображения; также выдаёт код для логического дисплея/процессора. | Не проходилось. |
| Восстановить последнюю удалённую область | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java), [`ModedInputHandler.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedInputHandler.java) | Запоминает build plans и восстанавливает их Alt + штатное меню схем; хранилище временное. | Не проходилось; после перезапуска данные не сохраняются по коду. |
| Взять/сбросить предмет из здания | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Запрашивает предмет штатным `Call.requestItem`; приоритет: sand, coal, затем первый предмет; есть rate limit. | Не проходилось; серверные права/лимиты не проверены. |
| Заменить связную область зданий | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Создаёт планы замены связных соседних зданий того же размера, что выбранный блок. | Не проходилось. |
| Удалить связную область зданий | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Находит связные соседние тайлы того же типа; обычное удаление делает планы разборки. | Не проходилось. |
| Соединить энергосеть | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Строит линию к ближайшему power-блоку вне выбранной области. | Не проходилось. |
| Залить область выбранным блоком | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Формирует build plans с ограничением `maxSchematicSize`. | Не проходилось. |
| Построить квадрат/круг | [`BuildingTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildingTools.java) | Рисует контур выбранным блоком; размер 1–512 меняется через панель/колесо/поле. | Не проходилось. |
| Скопировать блок с карты для инструментов | [`ModedDesktopInput.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedDesktopInput.java), [`HudFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/HudFragment.java) | Режим `pick` выбирает блок под курсором. | Не проходилось. |
| Редактировать слои карты в области | [`Internal.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/admins/Internal.java) | Выбирает floor/block/overlay и меняет карту через сетевые методы `Tile`. | Не проходилось; безопасность сетевого применения не доказана. |
| Рисовать карту кистью | [`Internal.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/admins/Internal.java) | Рисует выбранные слои карты в круге заданного радиуса. | Не проходилось; размер и частота не проверены. |
| Предметы ядра и изменение запасов | [`CoreInfoFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/CoreInfoFragment.java) | Выбирает команду, показывает предметы и изменение количества за короткое окно времени. | Не проходилось; данные чужой команды могут быть недоступны клиенту. |
| Энергия выбранного узла | [`CoreInfoFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/CoreInfoFragment.java), [`PowerBars.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/PowerBars.java) | Показывает граф power и позволяет выбрать узел/сеть. | Не проходилось. |
| Состав следующей волны и копирование | [`UnitsCache.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/UnitsCache.java), [`WaveApproachingDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/WaveApproachingDialog.java) | Считает число, суммарные здоровье/щит и боссов состава волны; копирует списки в буфер. | Не проходилось. |
| Визуальные инструменты | [`RendererTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/RendererTools.java), [`RendererConfigDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/RendererConfigDialog.java) | X-ray пола, скрытие юнитов, сетка/линейка, здоровье/патроны, радиусы юнитов/турелей/реакторов/overdrive, рамки дисплеев; power/status overlays, свет/темнота/fog. `fog` меняет `state.rules.fog`. | Не проходилось; локальность fog и сохранение переключателей не выяснены. |
| AI добычи | [`NetMinerAI.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ai/NetMinerAI.java) | Управляет юнитом-добытчиком с выбранным приоритетным ресурсом. | Не проходилось; ограничения сервера неизвестны. |
| Gamma AI: движение | [`GammaAI.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ai/GammaAI.java) | Режимы follow/cursor/circle управляют движением и наведением выбранного юнита. | Не проходилось. |
| Gamma AI: помощь/ремонт/разрушение | [`GammaAI.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ai/GammaAI.java) | Копирует планы игрока, строит/ремонтирует командные планы; destroy подключён к событиям разрушения. | Не проходилось. |
| Список игроков: поиск, просмотр, статус Scheme-Size | [`PlayerListFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/PlayerListFragment.java), [`ServerIntegration.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ServerIntegration.java) | Поиск, наблюдение за игроком, копирование имени; метки host/mod/vanilla/no-data через обмен subtitle. | Не проходилось с vanilla-клиентом. |
| Действия игрока из списка | [`PlayerListFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/PlayerListFragment.java) | Переход к AI и очистка юнита; штатное меню kick/ban/trace/team/admin по правам; votekick для подходящих союзников. | Не проходилось; права подтверждены только по вызовам API. |
| Админ-изменения мира | [`Internal.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/admins/Internal.java), [`ContentSelectDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/ContentSelectDialog.java) | Изменить управляемый юнит; создать 0–25 юнитов; применить/снять эффект; добавить/забрать предметы ядра на диапазоне −10000…10000; сменить команду/rainbow-team; поставить/убрать core shard; телепортировать; залить выбранные слои карты. | Не проходилось; авторитетность Internal не доказана. |
| Консоль: classic и multiline | [`ConsoleFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/ConsoleFragment.java) | Вкладки переключаются Tab; многострочный ввод использует историю штатного чата. | Не проходилось. |
| Консоль: повторяющиеся JavaScript-задачи | [`ConsoleFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/ConsoleFragment.java) | Задача повторяется через заданный интервал, выводится результат, отмена останавливает задачу. | Не проходилось; скрипт исполняется внутри игрового контекста. |
| Быстрые схемы по тегам | [`ShortcutFragment.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/ShortcutFragment.java), [`TagSelectDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/TagSelectDialog.java) | Показывает отмеченные схемы в hex quick menu; shortcut-настройки хранятся в settings. | Не проходилось. |
| Настройки мода | [`SettingsMenuDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/SettingsMenuDialog.java) | Панорамирование/zoom, разрешение схем, окно следующей волны, приветствие, проверка обновлений и subtitle. Настройка mobilebuttons исключена из цели ПК. | UI не проходилось; persistency не проверена. |
| CLaJ-комнаты и ссылки | [`ClajIntegration.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ClajIntegration.java), [`ManageRoomsDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/ManageRoomsDialog.java), [`JoinViaClajDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/JoinViaClajDialog.java) | Выбирает CLaJ-сервер, создаёт/закрывает комнаты, разбирает ссылку и подключается через внешний сервис/прокси. | Сервис и сетевой сценарий не проверялись. |
| Проверка и установка обновлений | [`SchemeUpdater.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/SchemeUpdater.java) | Читает latest GitHub release, запрашивает подтверждение и импортирует первый asset как обновление. | Не проходилось; перенос исходной логики/ресурсов не разрешён. |

## Бинды релиза 2.7.79

Реальные обработчики сопоставлены с enum [ModedBinding](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedBinding.java) и [ModedDesktopInput](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedDesktopInput.java). Это чтение кода, не проверка нажатием.

| Бинд по умолчанию | Действие | Сочетание с Left Alt |
|---|---|---|
| F12 | настройки админ-инструментов | — |
| Y | настройки визуализации | графические настройки |
| G | удержание открывает быстрый выбор схем, отпускание закрывает | — |
| F7 | показать/скрыть предметы ядра | — |
| I | выбор/включение AI | снять AI |
| F2 | изменить управляемый юнит | выбрать тип и количество спавна |
| F3 | выбрать эффект юнита | — |
| F4 | изменить предметы ядра | — |
| `;` | сменить команду игрока / включить rainbow-team | — |
| `'` | поставить или убрать core shard под игроком | — |
| Alt + штатная справка блоков | показать подсказки по сочетаниям | — |
| Alt + штатный respawn | удалить собственного юнита | — |
| Alt + штатное перемещение мышью | телепортироваться к курсору | — |
| Alt + штатный pan | переключить блокировку движения/свободную камеру | — |
| Alt + выбор схемы | переключить слой схемы | — |
| Alt + deselect | открыть/закрыть панель строительства | — |
| Alt + меню схем | восстановить последнюю удалённую область | — |
| Alt + rotate placed | взять/сбросить предмет из здания | — |

Бинды используют старый API Arc `KeyBinds`; фактические конфликты с V8 и другими модами не тестировались.

## Сравнение версий и порта после аудита

| Сравнение | Подтверждённое различие | Решение для паритета |
|---|---|---|
| Установленный архив ↔ O279 | Размер обоих артефактов 387350 байт. `mod.hjson`, 5 переводов, иконка, JS-скрипт и спрайт имеют те же Git blob SHA, что файлы тега. SHA-256 локального ZIP записан в отчёте; официальный asset digest не опубликован, бинарник скачать не удалось. | O279 — лучший исходный baseline, но совпадение байтов классов не доказано. |
| O279 ↔ upstream commit `f6c50f484ad690bef73c26a8300001b4e75b2ab2` | В старом `src/java/scheme`: 45 общих Java-файлов, 23 одинаковых blob, 22 изменённых; 7 файлов релиза отсутствуют. Текущие metadata переключают entrypoint на `schema.Main` версии 3.0.0 и добавляют отдельный `schema/` с 23 Java-файлами. | Старое `scheme/` из main не подменяет релизный baseline. `schema/` сравнивать отдельно; TODO не является функцией. |
| O279 ↔ порт commit `9327f72eed19ef957bb9b3e74ea03120cb7dd8e1` | В `src/java/scheme`: 44 общих пути, 16 одинаковых blob, 28 изменённых; порт добавляет 10 scheme-путей и не включает 8 оригинальных. Всего 143 Java-файла (99 путей сверх оригинала), включая CLaJ и серверно-клиентские классы. | Серверные команды/rule setter — дополнения порта, не исходный паритет. Автор указывает 156–159; работа на 160.7 не подтверждена. |

## Итог и оставшиеся проверки

- **Исходники:** все 52 Java-файла и основной bundle O279 инвентаризированы; отдельные функции перечислены в таблицах. Порт и новая ветка upstream сравнивались как самостоятельные источники.
- **Windows 160.7:** приложение запущено в изолированной копии, журнал показывает обнаружение O279 загрузчиком и последующие сообщения загрузки; лог `work/audit/runtime-win-no-steam/stdout.log`. В копии изменён только Steam modifier для обхода Steam API; это не тест запуска через Steam. Меню, клавиши и игровые действия не проходились.
- **Linux:** не проверен; в среде нет Linux/WSL/Docker/Podman. JVM/desktop-структура и возможные различия описаны в отчёте, но это не платформенный тест.
- **Vanilla-клиент + host-only mod:** исходник Mindustry 160.7 исключает `hidden`-моды из списка несовместимых; `mod.hjson` O279 содержит `hidden: true`. Это позволяет проектировать такой сценарий. Реальный клиент не подключался; совместимость пакетов и синхронизация конкретных операций не доказаны.
- **Dedicated/headless:** не основная цель пользователя. В O279 три разных админ-пути (`Internal`, `SlashJs`, `Darkdustry`) с разными зависимостями и проверками прав; наличие кнопки не доказывает серверную авторитетность. Серверный протокол порта нельзя приписывать O279.
- **Лицензия:** у O279 нет подтверждённой лицензии повторного использования. MIT порта не доказывает права на унаследованный код, ресурсы или CLaJ. Перенос кода и ассетов без разрешения не рекомендован.

Закрыта **инвентаризация исходников и сравнительная часть**. Полный функциональный паритет остаётся непроверенным до игровых сценариев. Практический тест подключения и действий хоста ограничен недоступностью управления окном и вторым клиентом в этой среде; Linux-рантайм также отсутствует. См. сценарии в [отчёте](01_discovery_report.md).

## Системные эффекты, параметры и вспомогательные классы O279

Эти функции/побочные эффекты также входят в исходную поверхность Scheme-Size и должны учитываться при проектировании чистой реализации.

| Функция / эффект | Источник O279 | Подтверждённое поведение | Проверка |
|---|---|---|---|
| Подмена desktop-ввода и окон игры | [`Main.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Main.java), [`ModedDesktopInput.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedDesktopInput.java) | Заменяет `control` input, библиотеку схем и фрагмент списка игроков; переопределяет обработку удаления/размещения планов, верхний/нижний рендер ввода и движение. Может конфликтовать с другими модами и штатными клавишами. | Приложение прошло этап загрузки мода; работа input не подтверждена. |
| Переадресация предметов дистрибьютором | [`Main.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Main.java) | Глобально меняет `Blocks.distributor.buildType`: при управляемом и стреляющем юните пытается направить предмет в здание возле его цели. | Не проверено в игре. |
| Режим без рамок Logic Display | [`Main.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Main.java), [`RendererTools.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/RendererTools.java) | Подменяет build type всех `LogicDisplay`, чтобы при флаге `borderless` отрисовывать буфер без границы. | Не проверено. |
| Глобальный GlyphLayout и форматирование | [`ModedGlyphLayout.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/moded/ModedGlyphLayout.java) | Устанавливает свой пул `GlyphLayout`; адаптирует markup-цвета, перенос и обрезку текста, используя reflection к внутренним методам Arc. | В журнале нет исключения при загрузке приложения; фактическое форматирование и масштабирование UI не проверялись. |
| Максимальный размер карты редактора | [`MapResizeFix.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/MapResizeFix.java) | Меняет `MapResizeDialog.maxSize` на 5000 и поле размера на 4 символа. | Не проверено. |
| Кеш тайлов/построек | [`BuildsCache.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/BuildsCache.java) | Кеширует карту, строения, турели, реакторы и overdrive для быстрых выборок рендера и инструментов. | Производительность не измерялась. |
| Кеш юнитов и wave info | [`UnitsCache.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/UnitsCache.java) | Кеширует скрытые юниты для локального рендера; считает груз/щит управляемого юнита и состав врагов/боссов волны. Удаление из draw-группы — локальная визуализация, не удаление сущности. | Не проверено. |
| Ограничение частоты команд и сброса предметов | [`MessageQueue.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/MessageQueue.java) | Ставит чат-команды в очередь, ограничивает отправку до 15 за 2 секунды; действия drop ограничены 30 в секунду. Очередь очищается при загрузке мира. | Не проверено. |
| Rainbow team | [`RainbowTeam.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/tools/RainbowTeam.java) | Периодически меняет цель между командами, отобранными по яркости/насыщенности; очистка при смене мира и выходе игрока. | Не проверено; штатная синхронизация команд отдельно не тестировалась. |
| Статус пользователей Scheme-Size | [`ServerIntegration.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ServerIntegration.java) | Обмен subtitle через custom packets отмечает host/пользователя мода; на нескольких заданных адресах ответ намеренно не отправляется. Это метаданные в списке игроков, не сетевой сервер для админ-команд. | Не проверено с vanilla-клиентом. |
| `Backdoor` | [`Backdoor.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/Backdoor.java) | Класс пишет шуточные сообщения и генерирует случайный URL как текст лога; в просмотренном коде сетевых действий нет. | Код проверен; вызов этого класса в runtime не подтверждён. |

Настройки из [`SettingsMenuDialog.java`](https://github.com/xzxADIxzx/Scheme-Size/blob/7936af9a36a46d752c9e5e3d73ebec2b48ca910b/src/java/scheme/ui/dialogs/SettingsMenuDialog.java): `panspeedmul`, `maxzoommul`, `minzoommul` — старт 4, диапазон 4–20; `hardscheme=false` включает разрешение схем после загрузки мира; `approachenabled=true` включает уведомление следующей волны; `welcome=true`; `check4update=true`; `subtitle` по умолчанию сообщает “I am using Scheme Size btw”. `mobilebuttons=true` присутствует только как mobile preference и не входит в целевой продукт. Админ-настройки по умолчанию: инструменты выключены, `adminsalways=false`, `adminsstrict=false`, backend `Internal` (`adminsway=0`).
