# Desktop design port to Android

## Источник истины

Визуальный эталон Android — desktop-файлы:

- `190x4-Ninety/src/styles/tokens.css`;
- desktop card/sidebar/settings styles;
- desktop assets и анимационные состояния.

Material 3 используется как инфраструктура Compose, но не как визуальный пресет.

## Перенесённые токены

- neutrals `ink-0..4`;
- borders `line-1..3`;
- top edge highlight;
- overlays, shine и strong shadow;
- text hierarchy `hi/mid/lo/faint`;
- status colors;
- accent/bright/deep/soft/glow;
- radius scale `6/10/14/18/24`;
- spacing scale `4/8/12/16/24/32/48/64`;
- Inter Tight и JetBrains Mono roles.

## Палитры

Android должен поддерживать весь desktop-каталог:

1. Kurogane
2. Shiro
3. Sakura Haze
4. Cyan
5. Glacier
6. Midnight
7. Synthwave
8. Ronin Violet
9. Matrix
10. Amber Glass
11. Mono
12. Command Center

Shiro, Sakura, Glacier, Midnight, Ronin и Amber меняют весь material stack, а не только accent.

## Базовые компоненты

Все экраны должны собираться из общего набора:

- `PremiumCard` — ink surface, line border, top hairline;
- `SurfaceCard` — settings/group material;
- `ScreenHeader` — kicker/title/subtitle;
- `IconTile`;
- `PillButton`;
- `PingPill`;
- `ToggleRow`;
- `SelectionCard`;
- `MetricTile`;
- `EmptyState`;
- `BottomNavigation`.

Запрещено вручную повторять card gradient/border/radius на каждом экране.

## Responsive правила

Телефон:

- bottom navigation;
- один столбец;
- safe area и edge-to-edge;
- крупная primary action зона;
- минимум 48dp для интерактивных целей.

Планшет/landscape:

- navigation rail;
- двухколоночные Profiles/Nodes/Settings;
- hero и метрики ближе к desktop-композиции;
- ограниченная ширина контента вместо растягивания на весь экран.

## Приоритет переноса экранов

1. Главная: hero, connection state, active source, traffic.
2. Профили: desktop premium cards и usage metadata.
3. Ноды: Auto, ping, selected/effective node.
4. Настройки: desktop sections и theme gallery.
5. Update modal, diagnostics, empty/error states.

## Motion

Использовать спокойные desktop easing-паттерны:

- обычные переходы 180–240ms;
- emphasized transitions 280–420ms;
- glow и breathing только для connection hero;
- отключать декоративное видео/сложную анимацию при reduced motion и battery saver.

## Критерий готовности дизайна

Экран считается перенесённым, когда:

- использует общие токены и компоненты;
- корректно выглядит во всех 12 темах;
- работает на телефоне и планшете;
- поддерживает длинные русские строки;
- не теряет контраст в светлых и Mono-темах;
- имеет Compose preview минимум для Kurogane, Shiro и Amber.
