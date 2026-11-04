# Scheme-Size: исследование upstream и портов

## Идентификация

Upstream: [xzxADIxzx/Scheme-Size](https://github.com/xzxADIxzx/Scheme-Size). В описании репозитория сказано: «No longer just increases the schema size». Репозиторий содержит Java-код, build/dependency scripts, `mod.hjson` и конфигурационные ресурсы.

**Цель пользователя:** сохранить функционал Scheme-Size целиком, а затем расширить и улучшить мод. Поэтому список из `mod.hjson` ниже — подтверждённый минимум для аудита, но не исчерпывающая спецификация. ИИ-агент должен исследовать актуальный код и интерфейс каждой upstream-функции до реализации и приёмки паритета.

## Подтверждённые метаданные

На основной ветке `mod.hjson` указывает:

| Поле | Значение в проверенном upstream |
|---|---|
| `name` | `schema` |
| `displayName` | `Schema` |
| `version` | `3.0.0` |
| `main` | `schema.Main` |
| `minGameVersion` | `146` |
| `hidden` | `true` |

Заявленные категории функций: flexible control settings, building tools, shortcuts for schematics, display of additional info, advanced developer console, Copy Link and Join. Источник: [upstream mod.hjson](https://github.com/xzxADIxzx/Scheme-Size/blob/main/mod.hjson).

Также метаданные предупреждают, что мод может быть несовместим с другими модами и рекомендуют ванильную игру. Это наблюдение upstream, а не доказательство, что каждая версия всегда конфликтует.

## Совместимость

`minGameVersion: 146` — заявленный upstream минимум на текущем файле. Он не означает, что текущий код протестирован на V8 Build 160.7. API-мод может перестать собираться/загружаться даже если метафайл разрешает установку. Сверь конкретный commit, релизный JAR и migration guide.

В верхнем списке файлов upstream root не виден файл LICENSE. **[ВЫВОД]** Нельзя считать разрешённым повторное использование кода/ассетов без дополнительной проверки лицензии. Для «почти с нуля» безопаснее считать upstream перечнем желаемых идей и самостоятельно написать код и ресурсы, пока условия reuse не подтверждены.

## Независимый порт

[Modustry: Scheme-Size](https://modustry.com.br/mod/scheme-size) описывает отдельный порт Mithra/ItsXeroc и указывает, что оригинальный автор в нём не участвует. Страница заявляет поддержку Mindustry 156–159 и перечисляет более широкий набор: схемы до 512, building/renderer tools, admin tools с server sync, AI, CLaJ, keybinds и advanced console.

Это **отдельный порт**, не upstream и не гарантия, что он работает с Build 160.7. Использовать его только как дополнительный источник требований/UX и подтверждать все подробности по фактическим исходникам/релизам.

## Что ещё надо изучить перед parity

- Точные лимиты схем по ширине/высоте, ограничения сериализатора и граничные случаи импорта.
- Как upstream изменяет UI/сохранение схем и какие платформы поддерживает.
- Список горячих клавиш и конфликтов.
- Какие building tools — обычные помощники, а какие меняют правила/ресурсы/юнитов.
- CLaJ — точный сетевой протокол, внешние зависимости, поддерживаемые серверы.
- Как отделяются client-only и dedicated-server code paths.
- Какие ассеты отсутствуют лицензией или используют ванильные ресурсы.

## Источники

- [Upstream repository](https://github.com/xzxADIxzx/Scheme-Size)
- [Upstream metadata](https://github.com/xzxADIxzx/Scheme-Size/blob/main/mod.hjson)
- [Upstream releases](https://github.com/xzxADIxzx/Scheme-Size/releases)
- [Independent port page](https://modustry.com.br/mod/scheme-size)
- [Third-party source analysis: HUD/build tools](https://deepwiki.com/xzxADIxzx/Scheme-Size/4.1-hud-and-building-tools) — вторичный/автоматически составленный материал, перепроверять.
