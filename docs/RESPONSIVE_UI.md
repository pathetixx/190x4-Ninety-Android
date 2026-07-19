# Responsive UI contract

## Цель

Android-клиент использует тот же визуальный язык, что desktop-Ninety, но не копирует Windows-разметку буквально. Навигация и композиция меняются по доступной ширине, а VPN/data/runtime API остаются едиными для всех размеров окна.

## Breakpoints

| Класс | Ширина | Навигация | Контент |
|---|---:|---|---|
| Compact | `< 600 dp` | нижняя панель | одна колонка, 16 dp поля, обязательный вертикальный scroll для высоких экранов |
| Medium | `600–1099 dp` | rail 84 dp | центрированный контент до 920 dp, adaptive grids |
| Expanded | `>= 1100 dp` | sidebar 236 dp | контент до 1180 dp, 32 dp поля, двухпанельные композиции |

Breakpoint определяется через `layoutMetrics(width)` в `ui/layout/Adaptive.kt`. Экраны не должны самостоятельно вводить другие пороги.

## Общая оболочка

- Compact сохраняет привычную нижнюю навигацию Android.
- Medium использует узкий rail, чтобы не отнимать пространство у планшета или landscape-телефона.
- Expanded использует sidebar в стиле desktop-Ninety: бренд, состояние туннеля, навигация, live-трафик и активный профиль.
- Системные safe-drawing insets применяются оболочкой, а не каждым экраном отдельно.
- Выбранный раздел хранится через `rememberSaveable` и переживает пересоздание Activity.

## Главная

Compact/Medium:

- профиль сверху;
- hero подключения по центру;
- активная нода снизу;
- Medium дополнительно показывает live-session card;
- вся композиция прокручивается при малой высоте или увеличенном шрифте.

Expanded:

- большой hero слева;
- профиль, активная нода и live-сессия справа;
- текущий профиль вынесен в header;
- ping, трафик, Auto effective node и ошибки используют существующие `VpnController`/`ClashMonitor` данные.

## Профили

- Compact: одна карточка в строке.
- Medium/Expanded: `LazyVerticalGrid` с адаптивными колонками не уже 320 dp.
- Import dialog ограничен шириной 560 dp и остаётся пригодным для телефона.
- Refresh, выбор профиля, удаление и runtime reload используют прежнюю бизнес-логику.

## Ноды

- Compact: одна карточка в строке.
- Medium/Expanded: adaptive grid с минимальной шириной карточки 286 dp.
- Auto/urltest занимает полную строку.
- Карточка показывает имя, ping, протокол, безопасность, транспорт и endpoint.
- Сортировка по задержке, ручной выбор, reload активного туннеля и FAB повторного теста сохранены.

## Настройки

Compact/Medium:

- список разделов;
- drill-down в выбранный раздел;
- кнопка возврата и отдельный scroll раздела.

Expanded:

- master-detail: постоянный список разделов слева и выбранные параметры справа.

Доступны все прежние группы: Общие, Оформление, Маршрутизация, DNS, Локальный доступ, TLS, Multiplex, Логи и О программе. В проекте остаётся одна responsive-реализация; старый мобильный экран удалён.

## Инварианты

- UI не строит sing-box JSON и не читает Room напрямую.
- `Store`, `Options`, `Prefs`, `VpnController`, `ClashMonitor` и `NinetyVpnService` остаются источниками данных и действий.
- Auto является валидным выбором во всех размерах окна.
- Смена профиля/ноды во время подключения отправляет generation-safe reload.
- Темы используют общий каталог из 12 desktop palettes.
- Реальные скриншоты для README делаются только с собранного APK; generated mockups не считаются проверкой.

## Device smoke matrix

Перед переводом PR из draft проверить:

1. Compact portrait: 360–430 dp, нижняя навигация, hero, grids, dialogs и dropdowns.
2. Compact/Medium landscape: низкая высота, прокрутка без обрезания hero и header.
3. Tablet portrait/landscape: 600–1099 dp, rail и adaptive grids.
4. Expanded/freeform: от 1100 dp, sidebar, двухколоночная Главная и master-detail Настройки.
5. Font scale: 1.0 и 1.3 минимум.
6. Темы: одна тёмная и Shiro/light.
7. VPN flow: start, stop, reload, Auto, смена профиля и ноды во время Connecting.
8. Профили: clipboard import, ручной ввод, refresh и delete.
9. Логи: refresh, copy, share и clear.
10. OTA: check update и открытие GitHub.

CI подтверждает компиляцию, lint и создание APK, но не заменяет этот real-device smoke-test.
