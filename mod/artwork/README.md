# Общая иконка Plus/God

Создана одна новая растровая иконка встроенным image_gen. Игровые и upstream-спрайты в генерацию не передавались.

- icon-master.png — неизменённый результат генерации, 1254×1254.
- ../src/main/resources/icon.png — экспорт 256×256 для обоих JAR.
- icon-64.png — уменьшенная проверка читаемости. Экспорты подготовлены Java ImageIO с bicubic интерполяцией, повторная генерация не выполнялась.

Команда экспорта из корня проекта: java work/PrepareModIcon.java mod/artwork/icon-master.png mod/src/main/resources/icon.png mod/artwork/icon-64.png. work является локальным инструментарием проверок и не входит в публичные исходники; все три готовых PNG сохранены в проекте.

Точный prompt:

> Generate exactly ONE square app/mod icon image for Scheme-Size Plus and God, shared by both editions. An original industrial geometric emblem: a small arrangement of three or four bold light silver rectangular building tiles enclosed by four prominent selection-frame corner brackets, with one restrained amber accent tile/corner. Graphite charcoal background, crisp strong silhouette, balanced centered composition, slight game-interface industrial depth but not photorealistic, not a 3D scene. Designed to remain clearly recognizable at 64 by 64 pixels. Generous safe margins, simple flat broad shapes, no tiny details, no grid of hairlines. No text, no letters, no numbers, no crown, no existing brand logo, no Mindustry sprites or copied game assets. Full square opaque background, no rounded exterior mask. Deliver one icon only, not a sheet, not a mockup.
