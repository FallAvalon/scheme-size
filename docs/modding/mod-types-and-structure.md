# Моддинг Mindustry: выбор типа и структура проекта

## Быстрый выбор

| Если нужно… | Подход |
|---|---|
| добавить блоки, предметы, юнитов, рецепты, карты, планеты, sprites | JSON/HJSON content mod |
| поменять отдельные поля существующего контента для карты/сервера | data patch |
| добавить UI, хоткеи, действия редактора и глубокое runtime-поведение | Java/JVM mod или скриптовый mod при проверке API |
| только серверные команды/режимы и серверный UI | server plugin |
| автоматика внутри построенной базы | Mindustry Logic, не мод |

Официальная Wiki описывает контентные моды как набор ассетов с необязательными script-файлами. JVM-моды наследуются от `mindustry.mod.Mod`; плагины — от `mindustry.mod.Plugin` и предназначены для сервера. Data patches меняют существующие значения и не добавляют новые механики. [Modding Intro](https://mindustrygame.github.io/wiki/modding/1-modding/) · [JVM/Plugins](https://mindustrygame.github.io/wiki/modding/2-plugins/) · [Data Patches](https://mindustrygame.github.io/wiki/datapatches/)

## HJSON и content

Обычный data-mod обычно имеет:

```text
mod.hjson
content/items/
content/blocks/
content/units/
content/liquids/
maps/
bundles/
sounds/
schematics/
scripts/
sprites/
sprites-override/
```

Минимальная метаинформация включает имя, автора, версию и описание. Формат HJSON допускает более читаемую структуру, чем строгий JSON. Названия файлов рекомендуется писать в нижнем регистре с дефисами.

## Java/JVM-мод

В корне лежит `mod.hjson`; `main` указывает полностью квалифицированное имя класса:

```hjson
name: scheme-size
author: Example
version: 0.1.0
main: example.schemesize.SchemeSizeMod
minGameVersion: 160
```

`main` класс обычно наследует `mindustry.mod.Mod`; API игры и Arc в Gradle объявляются `compileOnly`, чтобы не упаковывать копию игровых классов в свой JAR. Начни с [официального шаблона](https://github.com/Anuken/MindustryJavaModTemplate), но проверь его Gradle/JDK configuration на целевой V8 build.

## Server plugin

Плагин обычно имеет `plugin.hjson`, класс-наследник `mindustry.mod.Plugin` и располагается в `config/mods/` каталога сервера. Клиенту не обязательно устанавливать server-only plugin. Если мод содержит одновременно UI-клиент и серверные функции, проектируй server/client lifecycle явно; один класс с безусловными обращениями к UI не считается headless-safe.

## Scripts

JavaScript mod scripts используют Mindustry mod loader и доступные bindings. Это не то же самое, что mlog: `scripts/` исполняется как часть мода, а mlog загружается во внутриигровой процессор. Проверяй конкретный binding и поддержку сборки, прежде чем выбирать JS для сложной задачи.

## Data patch: полезные границы

Patch может менять существующие поля блоков, юнитов, рецептов, статов и отображения. Но он не является полноценной заменой мода: документация перечисляет ограничения на новые механики, размеры блоков, некоторые текстуры/поля и повторную инициализацию. Patch предназначен для уровня карты/сервера; не использовать его как единственный способ реализовать UI и инструменты схем.

## Установка и распространение

Официальная Wiki перечисляет пользовательские директории для Windows, Steam, Linux, macOS и Android. Для Java JAR в mod browser используется GitHub Release с артефактом `.jar`; для JS/JSON имеются отличающиеся правила получения исходников/release. Публикация требует валидного `mod.hjson`; текущая политика версии описана в [разделе о релизах](https://mindustrygame.github.io/wiki/modding/1-modding/).

## Ресурсы и диагностика

- [Официальная Modding Wiki](https://mindustrygame.github.io/wiki/modding/1-modding/)
- [Plugins & JVM Mods](https://mindustrygame.github.io/wiki/modding/2-plugins/)
- [Scripting](https://mindustrygame.github.io/wiki/modding/3-scripting/)
- [Types](https://mindustrygame.github.io/wiki/modding/5-types/)
- [Data Patches](https://mindustrygame.github.io/wiki/datapatches/)
- [Java template](https://github.com/Anuken/MindustryJavaModTemplate)
