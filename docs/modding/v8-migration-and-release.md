# Mindustry V8: миграция Java-мода и релиз

## Версионные метки

При сборе базы знаний текущий официальный Wiki snapshot указывал Build 160.7 (2026-11-01). Для проекта целесообразно закрепить:

- точный тестируемый build, например `160.x`;
- версию `mindustry`/`arc` compile API;
- Gradle wrapper и JDK;
- платформы сборки (Desktop, Android, Headless).

Сверять актуальную точку перед каждой сборкой: [GitHub Releases](https://github.com/Anuken/Mindustry/releases) и [официальная Wiki](https://mindustrygame.github.io/wiki/).

## Официальный V8 migration checklist

Официальная [8.0 Migration Guide](https://mindustrygame.github.io/wiki/modding/8-migrationv8/) отмечает изменения:

- JSON-моды, как правило, не требуют крупных изменений, но в V8 изменилось отображение контента на планетах.
- `Core.keybinds` удалено; пользовательские бинды используют новую систему `KeyBind#add`; значения binding теперь camelCase.
- `Core.atlas.getPixmap` больше нельзя вызывать вне `createIcons`.
- У `Building` удалена часть методов-getter; доступ следует сверять с актуальной моделью.
- `Player#unit()` может быть `null`; сначала проверяй `player.dead()`/валидность.
- `Units.null` удалено.
- Команды юнитов теперь content, коллекция команд — `Seq`, а не array.
- Поля видимости items удалены; для планет используется `shownPlanets`.
- `loopSound` удалён; циклический звук следует управлять в `Building` реализации.

Эти пункты относятся к конкретному API-переходу. Не лечить compile error простым переименованием без проверки поведения.

## Build и Java

Официальный [Mindustry Java Mod Template](https://github.com/Anuken/MindustryJavaModTemplate) содержит Gradle wrapper и поясняет compileOnly для API игры; его README предлагает JDK 17. Код desktop launcher на текущем upstream требует минимум Java 17, хотя пользователю рекомендует Java 25. Android target может потребовать отдельной конфигурации SDK. Проверяй фактические Gradle-плагины и версии в используемом шаблоне.

Пример общей структуры:

```text
mod.hjson
build.gradle.kts
gradlew.bat
src/main/java/<package>/
src/main/resources/
```

## Мод браузер и GitHub Releases

- JAR для Java-мода загружается из GitHub Release; мод должен быть скомпилирован.
- Официальная Wiki рекомендует в названии релиза указать target game build в квадратных скобках: `Scheme Size [v160]`.
- `minGameVersion` в V8-ветке указывается `160`.
- Для точного revision-тега, например `[v160.3]`, фильтр уже: он не равен общему `[v160]`.
- `version` в `mod.hjson` надо менять при новом выпуске; используемый формат должен быть semver.
- Для нескольких поколений игры удобно вести отдельные ветки/релизы. Не затирай основной код v8 фиксом следующего API поколения.

Правила меняются — перед публикацией читай текущий раздел [Mod Releases](https://mindustrygame.github.io/wiki/modding/1-modding/).

## V8 выпуск, тестовая матрица проекта

Предлагаемая матрица для Scheme-Size:

| Область | Проверить |
|---|---|
| Загрузка | чистая установка JAR, metadata, main class, ошибка несовместимого build |
| UI | desktop и touch (если заявлена мобильная поддержка), изменённый UI scale |
| Схемы | граничные размеры, импорт/экспорт, перезапуск, существующие пользовательские файлы |
| Одиночная игра | действия в sandbox и обычном режиме с проверкой правил |
| Сервер | обычный клиент, локальный host, dedicated/headless; отсутствующая server-side часть |
| Сеть | права, invalid request, отключение клиента, синхронизация состояния |
| Совместимость | ванильная игра и отдельно перечень поддерживаемых модов |

Это проектный чек-лист для будущего тестирования. При текущем запросе код и тесты не запускались.
