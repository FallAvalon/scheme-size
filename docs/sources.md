# Источники и правила проверки

## Приоритет источников

1. Исходный код Mindustry для целевого build и официальные Javadocs.
2. Официальная Wiki Mindustry и официальные release notes.
3. Код/метаданные самого Scheme-Size — только для фактов о его upstream.
4. Страницы независимых портов и сторонние разборы — вспомогательно, с обязательной пометкой.
5. Форумы и Reddit — полезны для обнаружения проблем/кейсов, но не источник окончательной API-истины.

## Официальные источники игры

- [Mindustry Wiki](https://mindustrygame.github.io/wiki/)
- [Официальный исходный код](https://github.com/Anuken/Mindustry)
- [Официальные релизы](https://github.com/Anuken/Mindustry/releases)
- [Javadocs](https://mindustrygame.github.io/docs/)
- [Modding intro](https://mindustrygame.github.io/wiki/modding/1-modding/)
- [Plugins/JVM mods](https://mindustrygame.github.io/wiki/modding/2-plugins/)
- [Scripting](https://mindustrygame.github.io/wiki/modding/3-scripting/)
- [Data patches](https://mindustrygame.github.io/wiki/datapatches/)
- [Types](https://mindustrygame.github.io/wiki/modding/5-types/)
- [V8 Migration Guide](https://mindustrygame.github.io/wiki/modding/8-migrationv8/)
- [Logic intro](https://mindustrygame.github.io/wiki/logic/0-introduction/)
- [Logic glossary](https://mindustrygame.github.io/wiki/logic/1-glossary/)
- [Logic editing](https://mindustrygame.github.io/wiki/logic/2-editing/)
- [Logic variables](https://mindustrygame.github.io/wiki/logic/3-variables/)
- [V8 Build 147 official notes](https://store.steampowered.com/news/posts/?appgroupname=Mindustry&appids=1127400&enddate=1744600127&feed=steam_community_announcements)
- [Steam store description](https://store.steampowered.com/app/1127400/Mindustry/)
- [Official Java Mod Template](https://github.com/Anuken/MindustryJavaModTemplate)

## Scheme-Size sources

- [Upstream repository](https://github.com/xzxADIxzx/Scheme-Size)
- [Upstream mod.hjson](https://github.com/xzxADIxzx/Scheme-Size/blob/main/mod.hjson)
- [Upstream releases](https://github.com/xzxADIxzx/Scheme-Size/releases)
- [Independent port information](https://modustry.com.br/mod/scheme-size)
- [Secondary DeepWiki analysis](https://deepwiki.com/xzxADIxzx/Scheme-Size/4.1-hud-and-building-tools)

## Обновление базы

Обновляй дату `checked_on` и build, если меняется целевая игра. Для изменения API открывай ссылку на точный tag/commit, а не только на `master`. Для сообщений «последняя версия» всегда проверяй официальный release list и дату результата. Любую стороннюю функцию маркируй именем проекта-порта.
