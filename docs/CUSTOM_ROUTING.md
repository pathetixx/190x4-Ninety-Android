# Custom routing contract

## Цель

Пользовательские правила маршрутизации должны одинаково трактоваться UI, persistence и sing-box config builder. Правила не хранят произвольный JSON и не редактируют итоговый конфиг напрямую.

Единый типизированный контракт находится в `core:model`:

```text
RoutingRule
  id
  enabled
  type
  match
  values[]
  action
```

Порядок элементов списка является приоритетом: **первое совпадение побеждает**.

## Типы совпадений

### Domain

Поддерживаемые режимы:

- `SUFFIX` → sing-box `domain_suffix`;
- `EXACT` → sing-box `domain`;
- `KEYWORD` → sing-box `domain_keyword`.

Sanitizer:

- удаляет схему URL;
- удаляет путь, query и fragment;
- удаляет wildcard `*.`;
- удаляет завершающую точку и numeric port;
- приводит к lower-case;
- преобразует IDN через `IDN.toASCII`;
- отклоняет localhost, пробелы и некорректные labels.

### IP

Поддерживаются:

- IPv4 address;
- IPv4 CIDR;
- IPv6 address;
- IPv6 CIDR.

Одиночный IPv4 преобразуется в `/32`, одиночный IPv6 — в `/128`.

Sanitizer отклоняет:

- octet выше 255;
- IPv4 с ведущими нулями;
- prefix за пределами `0..32` или `0..128`;
- zone identifier `%interface`;
- неоднозначные или неполные значения.

### Android package

Android application ID переводится в sing-box `package_name`.

Примеры:

```text
org.telegram.messenger
com.discord
```

Package matching доступен только на Android 10 / API 29 и новее. На Android 8/9 package rules сохраняются безопасно, но не добавляются в Android config. Domain и IP rules работают независимо от этой возможности.

### Desktop process

`PROCESS_NAME` является общей моделью для desktop-клиента и переводится в `process_name` только при `RoutingPlatform.DESKTOP`.

Android builder никогда не пишет `process_name`. Android UI не позволяет создавать новые process rules, но умеет показать ранее импортированное desktop-правило как неподдерживаемое.

## Действия

| Ninety | sing-box |
|---|---|
| `PROXY` | `outbound: proxy` |
| `DIRECT` | `outbound: direct` |
| `BLOCK` | `action: reject` |

## Порядок в sing-box route

Custom rules вставляются после обязательных service rules:

1. `sniff`;
2. `hijack-dns`;
3. пользовательские правила в сохранённом порядке;
4. private/LAN direct rule;
5. regional rule sets;
6. ad/malware reject rule sets;
7. default outbound.

Это сохраняет работоспособность DNS/sniff и одновременно даёт пользовательскому правилу приоритет над регионом, LAN и встроенным ad-block.

Отключённое правило, правило без валидных значений или правило неподдерживаемой платформы полностью пропускается.

## Android connection owner

На Android 10+ `NinetyVpnService.findConnectionOwner()` использует:

```text
ConnectivityManager.getConnectionOwnerUid()
        ↓
PackageManager.getPackagesForUid()
        ↓
libbox ConnectionOwner.androidPackageName
```

Для shared UID возможны несколько package names. Ninety сначала выбирает пакет, который явно присутствует в активных пользовательских правилах, и только затем использует первый видимый package как fallback.

`@RequiresApi(29)` фиксирует API-контракт для Android lint. Android 8/9 используют libbox procfs fallback; приложение не обещает `package_name` matching на этих версиях.

## Package visibility

GitHub-distributed APK объявляет `QUERY_ALL_PACKAGES`, потому что VPN service должен преобразовать произвольный connection UID в package name для пользовательских package rules.

Это разрешение нельзя считать автоматически подходящим для публикации в Google Play. Перед отдельной Play-distribution потребуется policy review и, возможно, другой UX/manifest strategy.

## Persistence

Правила входят в `Options.Data` и сохраняются внутри существующего Preferences DataStore options JSON. Legacy SharedPreferences JSON остаётся rollback journal на период миграции.

Ограничения загрузки:

- максимум 128 правил;
- максимум 256 значений в одном правиле;
- пустые и невалидные правила удаляются sanitizer;
- отсутствующий ID заменяется UUID;
- дубликаты значений удаляются с сохранением первого вхождения.

## UI

Редактор расположен в `Настройки → Маршрутизация` и поддерживает:

- создание и изменение правила;
- domain/IP/package type;
- suffix/exact/keyword;
- proxy/direct/block;
- ввод по одному значению на строку;
- enable/disable;
- изменение приоритета вверх/вниз;
- удаление;
- предупреждения о платформенных ограничениях.

Изменения persistence происходят сразу. Активный VPN применяет новый config при следующем generation-safe reload или повторном подключении.

## Автоматические тесты

Core model tests фиксируют:

- domain normalization;
- IPv4/IPv6 и CIDR validation;
- package/process separation;
- invalid/drop/duplicate accounting;
- сохранение порядка.

Config tests фиксируют:

- Android `package_name`;
- desktop `process_name`;
- domain/IP mapping;
- proxy/direct/reject actions;
- вставку после sniff/DNS и перед LAN/region;
- пропуск disabled/empty/unsupported rules;
- byte-deterministic output.

## Device smoke matrix

Перед переводом PR из draft проверить на реальном устройстве:

1. Domain suffix + Proxy: поддомен идёт через VPN.
2. Domain exact + Direct: exact host идёт напрямую, соседний поддомен не совпадает.
3. Domain keyword + Block: matching request получает reject.
4. IPv4 `/32` и CIDR direct/block.
5. IPv6 `/128` и CIDR при включённом IPv6 mode.
6. Package Proxy/Direct/Block на Android 10+ минимум для двух приложений.
7. Shared UID, если доступно на тестовом устройстве: выбирается package, присутствующий в правиле.
8. Перемещение правила меняет приоритет первого совпадения.
9. Disable не удаляет правило, но убирает его из config.
10. Persistence после force-stop и перезагрузки устройства.
11. Изменение правил во время Connected + reload без stale runtime transition.
12. Android 8/9: package rule безопасно отсутствует в config, domain/IP продолжают работать.
13. Импортированный desktop process rule отображается как unsupported и не попадает в Android config.

CI подтверждает модели, JSON, Android compile и lint, но не заменяет сетевой smoke-test этих сценариев.
