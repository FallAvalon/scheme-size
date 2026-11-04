# Иконки Size Plus (S+) и Size God (SG)

04.11.2026, две отдельные генерации встроенным image_gen в режиме редактирования. Единственный референс — ранее созданный нами icon-master.png. Игровые спрайты и ресурсы Scheme-Size не использовались. Исходники сохранены без изменения: plus/master.png и god/master.png. Результаты визуально проверены; экспорты — 256×256 и 64×64.

Экспорт из корня проекта, JDK 17+:

```sh
java mod/tools/PrepareModIcon.java mod/artwork/plus/master.png mod/editions/plus/icon.png mod/artwork/plus/icon-64.png
java mod/tools/PrepareModIcon.java mod/artwork/god/master.png mod/editions/god/icon.png mod/artwork/god/icon-64.png
```

Точные запросы:

## Plus

Use case: precise-object-edit. Edit target is the provided original mod icon. Create ONE square finished icon for Size Plus (S+). Preserve the graphite opaque background and large four silver selection-frame corner brackets with the top-right amber corner; keep the same restrained industrial flat beveled style, strong silhouette and generous safe margins. Replace ONLY the center group of bars with a bold light silver block-letter S and a small but clearly legible amber plus sign to its right, reading exactly S+. Center the combined mark in the selection frame. Minimalistic, legible at 64x64, no fine details, no other text, no game sprites, no watermark. Deliver a single icon, not a sheet.

## God

Use case: precise-object-edit. Edit target is the provided original mod icon. Create ONE square finished icon for Size God (SG), companion to a Size Plus S+ icon. Preserve the graphite opaque background and large four silver selection-frame corner brackets with the top-right amber corner; keep the same restrained industrial flat beveled style, strong silhouette and generous safe margins. Replace ONLY the center group of bars with the bold geometric block letters SG, S in light silver and G in amber. Both letters are equal height, placed side by side, reading exactly SG; together centered inside the selection frame. Minimalistic, legible at 64x64, no fine details, no other text, no crown, no game sprites, no watermark. Deliver a single icon, not a sheet.
