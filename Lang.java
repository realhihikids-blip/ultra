package com.pvpclient.util;

import com.pvpclient.PvpClient;

import java.util.HashMap;
import java.util.Map;

/** Display-only translation (English keys stay in the code and in the config file). */
public final class Lang {
    private static final Map<String, String> RU = new HashMap<>();

    private Lang() {}

    public static boolean ru() {
        return PvpClient.settings() != null && PvpClient.settings().language.get() == 1;
    }

    public static String tr(String s) {
        return ru() ? RU.getOrDefault(s, s) : s;
    }

    private static void p(String en, String ru) { RU.put(en, ru); }

    static {
        // categories
        p("Combat", "Бой"); p("Visuals", "Визуал"); p("HUD", "HUD"); p("Settings", "Настройки"); p("Automation", "Автоматизация"); p("Movement", "Передвижение");
        p("Speed", "Скорость"); p("Multiplier", "Множитель"); p("Fly", "Полёт"); p("Fly Speed", "Скорость полёта");
        p("Legit Targets", "Цели (Legit)"); p("Angle Gate", "Порог угла");
        p("Mine Ore Only If Low Durability", "Руда только при износе кирки"); p("Low Durability Threshold %", "Порог износа %");
        p("Crystal Aura", "Кристалл Аура"); p("Target Range", "Дальность цели"); p("Place Range", "Дальность установки"); p("Min Target Damage", "Мин. урон по врагу"); p("Max Self Damage", "Макс. урон по себе"); p("Safety HP", "Запас HP"); p("Place Delay (ticks)", "Задержка установки (тики)"); p("Break Delay (ticks)", "Задержка взрыва (тики)"); p("Place Obsidian", "Ставить обсидиан"); p("Rotate Camera", "Поворот камеры");
        p("Krutilka", "Крутилка"); p("Legit", "Легит"); p("Chase Range", "Дальность погони"); p("Spin Radius", "Радиус кружения"); p("Spin Reach", "Дальность удара (крутилка)"); p("Auto Jump Crits", "Авто-прыжок + криты");
        p("Dungeon Bot", "Бот-данжер"); p("Scan Radius", "Радиус скана"); p("Ore Radius", "Радиус руды"); p("Eat Below", "Есть при сытости"); p("Pickaxe Min %", "Мин. прочность кирки %"); p("Descend To Y", "Спуск до Y"); p("Show Overlay", "Подсветка"); p("Overlay Radius", "Радиус подсветки");
        // modules
        p("KillAura", "Килл Аура"); p("TriggerBot", "Триггер Бот"); p("Hitbox", "Хитбокс");
        p("Player ESP", "ESP игроков"); p("Chest ESP", "ESP сундуков"); p("Player Info", "Инфо об игроках");
        p("CPS Counter", "Счётчик CPS"); p("FPS Counter", "Счётчик FPS"); p("Ping Counter", "Счётчик пинга");
        p("Keystrokes", "Клавиши"); p("Interface", "Интерфейс");
        // descriptions
        p("Practice mode: auto-aim, follow and hit (private worlds only)", "Тренировка: автонаводка, бег за целью и удары (только приватные миры)");
        p("Practice mode: attacks when the crosshair is on a target", "Тренировка: бьёт, когда прицел на цели (только приватные миры)");
        p("Enlarges other players' hitboxes (private worlds only)", "Увеличивает хитбоксы других игроков (только приватные миры)");
        p("Outline boxes around players (private worlds only)", "Рамки вокруг игроков (только приватные миры)");
        p("Colored outlines for chests, barrels and shulkers (private worlds only)", "Цветные рамки сундуков, бочек и шалкеров (только приватные миры)");
        p("Floating panel with name, armor, enchants and held item (private worlds only)", "Панель над игроком: ник, броня, зачарования, предмет (только приватные миры)");
        p("Clicks per second", "Клики в секунду"); p("Frames per second", "Кадры в секунду");
        p("Latency to the server", "Задержка до сервера"); p("WASD, mouse buttons and space", "WASD, кнопки мыши и пробел");
        p("Menu scale, animations and theme colors", "Масштаб меню, анимации, цвета и язык");
        // KillAura / TriggerBot settings
        p("Auto Hit", "Авто удар"); p("Auto Aim", "Автонаводка"); p("Aim At", "Целиться в");
        p("Aim Speed (deg/s)", "Скорость наводки (°/с)"); p("Auto Follow", "Бег за целью");
        p("Auto Sprint", "Авто спринт"); p("Target Range", "Дальность цели");
        p("Follow Stop Distance", "Дистанция остановки"); p("Attack Range", "Дальность удара");
        p("Attack FOV (no aim)", "Угол удара (без наводки)"); p("Cooldown Charge", "Заряд удара");
        p("Only Crits", "Только криты"); p("Strict Crits", "Строгие криты (без обычных ударов)"); p("Auto Jump (for crits)", "Авто прыжок (криты)");
        p("Crit Timeout (ticks)", "Тайм-аут крита (тики)"); p("Min Hit Interval (ticks)", "Мин. интервал ударов (тики)");
        p("Targets", "Цели"); p("Hit Through Walls", "Бить сквозь стены");
        p("Hitbox Size", "Размер хитбокса");
        // ESP
        p("ESP Color", "Цвет ESP"); p("Use Team Colors", "Цвета команд"); p("Highlight", "Подсветка");
        p("Highlight Color", "Цвет подсветки"); p("Distance", "Дистанция");
        p("Chests", "Сундуки"); p("Ender Chests", "Эндер-сундуки"); p("Barrels", "Бочки"); p("Shulker Boxes", "Шалкеры");
        p("Through Walls", "Сквозь стены"); p("Chest Color", "Цвет сундуков"); p("Trapped Chest Color", "Цвет сундуков-ловушек");
        p("Ender Chest Color", "Цвет эндер-сундуков"); p("Barrel Color", "Цвет бочек"); p("Shulker Color", "Цвет шалкеров");
        // Player Info
        p("Show Nicknames", "Показывать ники"); p("Status Dot", "Точка статуса"); p("Show Armor", "Показывать броню");
        p("Show Enchantments", "Показывать зачарования"); p("Show Held Item", "Показывать предмет в руке");
        p("Enchant Format", "Формат зачарований"); p("Text Size", "Размер текста"); p("Panel Scale", "Масштаб панели");
        p("Background Opacity", "Прозрачность фона"); p("Display Distance", "Дистанция показа");
        p("Background Color", "Цвет фона"); p("Border Color", "Цвет рамки"); p("Team Color Border", "Рамка цвета команды");
        // HUD
        p("Scale", "Масштаб"); p("Background", "Фон"); p("Text Color", "Цвет текста");
        p("Position X %", "Позиция X %"); p("Position Y %", "Позиция Y %"); p("Show Right Click", "Показывать ПКМ");
        // Interface
        p("GUI Scale", "Масштаб меню"); p("Animations", "Анимации"); p("Animation Speed", "Скорость анимаций");
        p("Accent Primary", "Основной акцент"); p("Accent Secondary", "Второй акцент"); p("Language", "Язык");
        // mode values
        p("Body", "Тело"); p("Head", "Голова"); p("Players", "Игроки"); p("Mobs", "Мобы"); p("Players + Mobs", "Игроки + Мобы");
        p("Short (P4)", "Короткий (P4)"); p("Full (Protection IV)", "Полный (Protection IV)");
        // new
        p("Target Lock", "Захват цели");
        p("KillAura and TriggerBot only attack the player you locked on", "Килл Аура и Триггер Бот бьют только захваченного игрока");
        p("Lock On Hit", "Захват при ударе"); p("Lock On Enable", "Захват цели в прицеле при включении");
        p("Switch On Hit", "Менять цель при ударе"); p("Target: ", "Цель: ");
        p("Style", "Стиль"); p("2D Box (through walls)", "2D рамка (сквозь стены)"); p("3D Box", "3D рамка"); p("Fill", "Заливка");
        p("Strict Crits", "Строгие криты (без обычных ударов)");
        // heal / buff / strafe
        p("Auto Heal", "Авто хил"); p("Auto Buff", "Авто баф");
        p("Uses a golden apple or healing potion when health is low (private worlds only)", "Ест золотое яблоко или пьёт зелье лечения при низком здоровье (только приватные миры)");
        p("Drinks potions to keep chosen effects active (private worlds only)", "Пьёт зелья, чтобы поддерживать выбранные эффекты (только приватные миры)");
        p("Heal Below (HP)", "Лечить при HP ниже"); p("Heal With", "Чем лечить");
        p("Golden Apple", "Золотое яблоко"); p("Healing Potion", "Зелье лечения"); p("Any", "Любое");
        p("Delay (ticks)", "Задержка (тики)");
        p("Speed", "Скорость"); p("Strength", "Сила"); p("Fire Resistance", "Огнестойкость");
        p("Resistance", "Сопротивление"); p("Regeneration", "Регенерация");
        p("Circle Strafe", "Бег кругами вокруг цели"); p("Strafe Switch (ticks)", "Смена стороны (тики)");
        // search
        p("Search...", "Поиск..."); p("Search results", "Результаты поиска");
        // Freecam
        p("Freecam", "Свободная камера"); p("Free-flying camera; your character stays where it is", "Свободная камера: персонаж остаётся на месте");
        p("Sprint Multiplier", "Множитель ускорения");
        // Block Trail
        p("Block Trail", "След из блоков"); p("Ghost blocks under your feet while you walk (visual only)", "Призрачные блоки под ногами при ходьбе (только визуально)");
        p("Block", "Блок"); p("Appearance", "Вид"); p("Outline", "Контур"); p("Filled", "Заливка"); p("Block Model", "Модель блока");
        p("Use Block Color", "Цвет блока"); p("Color", "Цвет"); p("Opacity", "Прозрачность"); p("Trail Length", "Длина следа");
        p("Lifetime (s)", "Время жизни (с)"); p("Preview Ghost Blocks", "Предпросмотр блоков впереди"); p("Preview Blocks", "Блоков впереди");
        p("Glass", "Стекло"); p("Stone", "Камень"); p("Obsidian", "Обсидиан"); p("Ice", "Лёд"); p("Slime Block", "Слизь");
        p("Gold Block", "Золото"); p("Diamond Block", "Алмаз"); p("Iron Block", "Железо");
        // Direction Indicators
        p("Direction Indicators", "Указатели направления");
        p("Arrows around the crosshair toward nearby players (private worlds only)", "Стрелки вокруг прицела к ближайшим игрокам (только приватные миры)");
        p("Max Players", "Макс. игроков"); p("Radius (px)", "Радиус (пикс.)"); p("Arrow Size", "Размер стрелки");
        p("Max Distance", "Макс. дистанция"); p("Show Distance", "Показывать расстояние"); p("Arrow Color", "Цвет стрелки");
        // Ender Pearl Trainer
        p("Ender Pearl Trainer", "Тренировка перлов");
        p("Pearl landing prediction and dummy aiming test (private worlds only)", "Предсказание падения перла и тест наводки по манекену (только приватные миры)");
        p("Track Throwers", "Чьи перлы отслеживать"); p("Other Players", "Другие игроки"); p("Nearest Player", "Ближайший игрок"); p("Including Me", "Включая меня");
        p("Show My Aim Prediction", "Показывать мой бросок"); p("Dummy Range", "Дальность манекена");
        p("Auto Aim At Dummy", "Автонаводка на манекен"); p("Auto Throw", "Автобросок"); p("Throw Delay (ticks)", "Задержка броска (тики)");
        p("Landing Color", "Цвет точки падения"); p("Path Color", "Цвет траектории");
        p("My throw", "Мой бросок"); p("Dummy", "Манекен"); p("Dummy: ", "Манекен: "); p("Dummy out of reach", "Манекен вне досягаемости");
        p("Pearl thrown by ", "Перл бросил: ");
        p("Smooth Camera", "Плавная камера"); p("Smoothing Speed", "Скорость сглаживания");
        // ui
        p("Keybind", "Клавиша"); p("Press a key...", "Нажмите клавишу..."); p("None", "Нет");
        p("PRIVATE WORLDS ONLY", "ТОЛЬКО ПРИВАТНЫЕ МИРЫ"); p("Keybinds", "Клавиши модулей");
        p("Click a button, then press a key. Backspace clears.", "Нажмите кнопку, затем клавишу. Backspace — сбросить.");
        p("Menu key: = (change in Options > Controls)", "Клавиша меню: = (меняется в Настройки > Управление)");
        p("Save config", "Сохранить"); p("Saved!", "Сохранено!"); p("=  open / close", "=  открыть / закрыть");
        p("Ping: local", "Пинг: локально"); p("Ping: ", "Пинг: "); p(" ms", " мс");
        p("LMB", "ЛКМ"); p("RMB", "ПКМ"); p(" cps", " кл/с");
    }
}
