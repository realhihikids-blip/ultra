# Blip Client (Fabric 1.21.4, Java 21)

Neon-violet and black ClickGUI (=), a custom Blip Client logo on the title screen, HUD widgets, practice combat modules and a floating Player Info ESP panel.

## Build
1. Install JDK 21.
2. Generate the Gradle wrapper once (needs Gradle 8.12+ installed): `gradle wrapper --gradle-version 8.12`
3. `./gradlew build`  (Windows: `gradlew.bat build`)
4. The mod is `build/libs/pvpclient-1.0.0.jar` (ignore `-sources.jar`).

## Install
Fabric Loader 0.16.10+, Fabric API 0.119.2+1.21.4, then drop the jar into `.minecraft/mods`.

## Run from source
`./gradlew runClient`

## Safety scope
KillAura, TriggerBot, Player ESP and Player Info only run in singleplayer / LAN-host worlds, or on servers
whose host you list under `authorizedServers` in `config/pvpclient.json` (servers you own or administer).
No rotation spoofing, no packet tricks, no hiding from anti-cheat.

## Features added in this version
- **Freecam** (Visuals): free-flying camera, your character stays put. Uses two small mixins (`CameraMixin`, `EntityMixin`).
- **Block Trail** (Visuals): visual-only ghost blocks under your feet; block type, appearance (outline / filled / block model), preview of blocks ahead.
- **Direction Indicators** (HUD): arrows around the crosshair toward nearby players, with distance. Private worlds only.
- **Ender Pearl Trainer** (Combat): predicts pearl landings, shows your own throw prediction, tests aiming at armor-stand dummies (auto aim / auto throw are off by default). Private worlds only.
- **Search bar** in the menu: filters modules and settings across all categories.


## GitHub Actions build

The workflow in `.github/workflows/build.yml` installs Gradle 8.12 with
`gradle/actions/setup-gradle@v4` and runs `gradle build`. This CI path does not
require `gradlew`.

## Gradle Wrapper status

The source ZIP supplied for this update contained `gradle-wrapper.properties`,
but did not contain `gradle-wrapper.jar`, `gradlew`, or `gradlew.bat`.
A complete, official Gradle Wrapper cannot be reconstructed safely without
the matching wrapper JAR. The CI workflow therefore builds using the Gradle
installation provisioned by `setup-gradle` instead of pretending that a
complete wrapper is present.

To generate the complete wrapper on a machine with Gradle 8.12 installed,
run `gradle wrapper --gradle-version 8.12` from the project root, then commit
`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.


## Dungeon Bot (Automation category)
Автономный бот внутри клиента: `src/main/java/com/pvpclient/module/bot/`.
Включается в ClickGUI (`=` → Automation → Dungeon Bot), можно назначить клавишу.
- EAT → DEFEND → REPAIR → MINE → LOOT → DESCEND → EXPLORE (конечный автомат, см. `DungeonBotModule`).
- Поиск данжей: spawner / chest / mossy_cobblestone (+ рельсы для шахт) в загруженных чанках, радиус 64–128.
- Добыча только видимых открытых руд в радиусе 10–15 блоков; кирка < 15% → запасная или крафт новой.
- Лут сундуков: ценные предметы, ключи и карточки (по имени и NBT).
- Подсветка: красный контур у блоков, которые бот сломает/поставит; голубая линия пути; жёлтые рамки — прыжки.
- Работает только в одиночной игре / LAN или на серверах из `authorizedServers` (`BotGuard`).

## Crystal Aura и KillAura «Крутилка» (Combat)
- **Crystal Aura**: обсидиан рядом с врагом → кристалл → взрыв, только если расчёт урона безопасен
  (Max Self Damage, Safety HP). Нужны обсидиан и кристаллы в инвентаре.
- **KillAura → Mode = Krutilka**: мгновенный захват цели, погоня, кружение, авто-прыжки и удар критом в падении.
- **Hitbox**: максимум размера поднят до 4.0.
Оба режима работают только в одиночной игре / LAN / на серверах из `authorizedServers` (`BotGuard`).

## Фиксы и новое (эта итерация)
- **Crystal Aura**: убран нереалистичный множитель ×1.5 к самоурону (из-за него модуль почти
  никогда не находил «безопасное» место) и заданы более реалистичные дефолты урона. Теперь
  в actionbar пишет, почему ничего не происходит (нет кристаллов / нет безопасного места).
- **Dungeon Bot**: цель руды стала «липкой» — бот больше не дёргается между соседними блоками
  руды в жиле и долбивает один блок до конца. Добавлен пилларинг (прыжок + блок под ноги) —
  бот строит ступени вверх, если не может шагнуть на готовую, а не просто прыгает на месте.
  Добыча руды теперь по умолчанию включается только при низкой прочности кирки
  (Mine Ore Only If Low Durability, порог настраивается).
- **KillAura → Legit**: переписан на захват одной цели (как в приложенном файле) и более
  строгую проверку угла перед ударом (и по рысканью, и по тангажу). Трюк с подменой
  `setOnGround` для обхода Vulcan НЕ перенесён — это уже не «практика механики», а обход
  защиты реального сервера, этим я не помогаю. Krutilka не изменена.
- **Movement**: добавлены Speed (множитель скорости через атрибут) и Fly (полёт как в
  креативе). Both ограничены BotGuard (singleplayer / LAN / authorizedServers), по той же
  причине, что и бот/крутилка/кристал-аура. Полноценный ноклип (пролёт сквозь блоки) не
  добавлен: у него нет практического применения даже в своём мире, кроме обхода защиты чужого
  сервера — это уже не входит в задачи клиента для практики.
