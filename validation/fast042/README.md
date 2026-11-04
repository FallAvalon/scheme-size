# Проверки 0.4.2

Профили workspace-* создаются рядом со скриптами, в Git не входят. Используются отдельные non-Steam desktop/server JAR точно 160.7. Установленный профиль не затрагивается. В игровом observer физический OS-ввод не выполняется: клавиши/колесо посылаются Arc, координаты указателя SDL задаются reflection.

UI:

```powershell
./validation/fast042/Run.ps1 -DesktopJar C:/path/to/non-steam-160.7.jar -JdkHome C:/path/to/jdk -GameJava C:/path/to/java.exe -UiScale 100
./validation/fast042/Run.ps1 -DesktopJar C:/path/to/non-steam-160.7.jar -JdkHome C:/path/to/jdk -GameJava C:/path/to/java.exe -UiScale 200 -Only god -SmallWindow
```

Observer явно включает подтверждения для старых сценариев отмены/смены целей, отдельно выключает их и проверяет немедленное выполнение. Новые действия проверяют default=false, родной DesktopInput.update/Renderer.targetscale при колесе с фокусом панели, настоящий content picker, природную стену/undo, сохранение выбранного слоя/стены, мгновенное убийство по фильтру и защиту игрока. Backgroundpause=false только в тестовом профиле, чтобы переключение фокуса SDL не подменяло проверку Esc автоматической паузой ОС.

Сеть, настоящий отдельный vanilla headless без установленного мода и клиент с одной редакцией:

```powershell
./validation/fast042/Remote.ps1 -DesktopJar C:/path/to/non-steam-160.7.jar -ServerJar C:/path/to/server-160.7.jar -JdkHome C:/path/to/jdk -GameJava C:/path/to/java.exe -Edition god
./validation/fast042/Remote.ps1 -DesktopJar C:/path/to/non-steam-160.7.jar -ServerJar C:/path/to/server-160.7.jar -JdkHome C:/path/to/jdk -GameJava C:/path/to/java.exe -Edition god -Strict $true
```

Повторить для -Edition plus. Подключение к 127.0.0.1:16589. Observer сервера создаёт обычный mega и регистрирует тестовый маркер попытки; не меняет обработку clientSnapshot или серверную проверку движения. Проверяются позиция на сервере и на клиенте после нескольких секунд: strict=false принимает телепорт без admin; strict=true возвращает позицию. Это локальная сеть, не WAN.

Промежуточные сбои: первая маршрутизация только через Scene capture не получала wheel без scrollFocus — заменена ранним InputProcessor. Затем текстовый selector «Блок» попал в штатную «Блокировки»; observer перешёл на именованную кнопку настоящего picker. Фиксированный тестовый указатель после панели требовал Scene.act для обновления hover. Для сетевого observer потребовалось обновить матрицу камеры после изменения её позиции, иначе ожидание центра не совпадало с мировым курсором. Отдельная сборка выявила, что Arc имеет InputProcessor с default-методами, а не InputAdapter; исправлено. Ни один неудачный прогон не выдаётся за PASS.