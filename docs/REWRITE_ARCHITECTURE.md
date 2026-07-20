# Ninety Android rewrite architecture

## Цель

Переписать Android-клиент вокруг уже доказанного `VpnService + libbox`, не перенося Windows-специфичный backend и не создавая вторую бесконтрольную копию desktop-логики.

Кроссплатформенность Ninety означает:

- единые модели и форматы;
- единые parser/config fixtures;
- одинаковые продуктовые правила;
- отдельные platform adapters для Windows и Android;
- нативный Android UI и lifecycle.

## Целевая структура

```text
:core:model
  ProxyNode/Profile/Selection/Options/Capabilities/RoutingRule

:core:parser
  share links + subscription normalization

:core:config
  deterministic sing-box JSON builder

:core:runtime
  VPN commands, phases, generation-safe transitions

:core:quality
  bounded history, health scoring, cooldown and stable Auto policy

:data
  Room, DataStore, migrations, encrypted secrets

:vpn:libbox
  VpnService, PlatformInterface, CommandServer/Client

:app
  Compose, adaptive shell, navigation, Android intents
```

## Правила зависимостей

```text
app -> data -> core:model
app -> core:parser -> core:model
app -> core:runtime
app -> core:quality
app -> vpn:libbox -> core:config -> core:model
core:config tests -> core:parser test fixtures
```

Core-модули не должны импортировать:

- `android.*`;
- Compose runtime;
- `io.nekohasekai.libbox`;
- конкретное хранилище;
- UI-строки.

## Реализованный parser boundary

`core:model` владеет нормализованными `ProxyNode` и `ProxyProtocol`. `core:parser` принимает share-link или subscription body и возвращает эти модели. Текущий Android `LinkParser` остаётся тонким compatibility adapter в legacy `Node`, поэтому `Store`, импорт и refresh не требуют рискованного одновременного переписывания.

Parser fixtures повторяют общий desktop-контракт для:

- VLESS, включая Reality, IPv6 и xHTTP `extra`;
- VMess base64 JSON;
- Trojan;
- Shadowsocks SIP002 и plugin metadata;
- Hysteria2 / `hy2`;
- TUIC;
- plain и base64 subscription bodies.

## Реализованный config boundary

`core:config` принимает только:

- `ConfigNode(id, ProxyNode)`;
- типизированный `ProxySelection`;
- неизменяемый снимок `SingBoxOptions`;
- опциональный путь лога.

Модуль детерминированно строит sing-box 1.13 JSON и не знает об Android, SharedPreferences, Compose или libbox. Android `ConfigBuilder` сохраняет прежнюю публичную сигнатуру и выполняет только преобразование legacy `Node` / `Options.Data` в core DTO.

Golden tests фиксируют:

- selector + urltest и стабильные outbound tags;
- VLESS/VMess/Trojan/Shadowsocks/Hysteria2/TUIC;
- Reality, uTLS, ALPN, WS/gRPC/HTTP/xHTTP;
- безопасную whitelist-трансляцию xHTTP `downloadSettings`;
- Shadowsocks plugin, Hysteria2 certificate pin, TUIC `disable_sni`;
- IPv4/IPv6, DNS, FakeDNS, regional rule sets и ad blocking;
- TLS fragmentation/tricks и stream multiplex;
- отсутствие multiplex на QUIC-протоколах;
- byte-deterministic output и раннюю валидацию некорректных DNS/options.

Подробный контракт описан в `docs/CONFIG_PORT.md`.

## Реализованный data boundary

`:data` владеет:

- Room database `ninety.db` со schema v1;
- таблицами `profiles` и `nodes`;
- foreign key `nodes.profileId -> profiles.id` с cascade delete;
- Preferences DataStore для настроек, активного выбора и bounded quality history;
- AES/GCM codec на Android Keystore;
- транзакционным gateway с обязательным read-back verification;
- migration marker, который ставится только после проверки Room и DataStore.

Синхронные Android facade `Store`, `Prefs` и `Options` пока сохранены, чтобы не переписывать UI и VPN одновременно. Они используют проверенный modern snapshot, а изменения dual-write в legacy rollback journal и новый store.

Rollback journal состоит из atomic JSON-файлов и checksum-манифеста. Неполная или повреждённая файловая запись не считается источником восстановления Room. Старые файлы пока не удаляются; cleanup разрешён только после upgrade smoke-test на реальном устройстве.

Полный протокол описан в `docs/DATA_MIGRATION.md`.

## Реализованный runtime boundary

`:core:runtime` владеет командами `Start`, `Reload`, `Stop`, фазами lifecycle и монотонным generation token. Completion или failure принимается только от актуального ticket; более новая команда немедленно делает старую долгую операцию stale.

Android `NinetyVpnService` выполняет platform effects одной FIFO-очередью:

```text
request command + advance generation
              ↓
single runtime executor
              ↓
libbox / TUN / notification / cleanup
```

Это гарантирует:

- поздний start не может вернуть `Connected` после stop;
- смена ноды/профиля во время `Starting` применяет последний config;
- повторные stop идемпотентны;
- stale failure не останавливает новый запуск;
- `CommandServer`, TUN fd и network callback закрываются через одну resource boundary.

`VpnController` публикует `StateFlow<VpnSnapshot>`. Для существующих Compose-экранов временно сохранён совместимый state mirror; source of truth остаётся StateFlow.

Полный контракт и device smoke-test описаны в `docs/RUNTIME_STATE_MACHINE.md`.

## Реализованный UI boundary

Compose использует единый `NinetyLayoutMetrics` и три класса окна:

- Compact `< 600 dp`: bottom navigation и одна колонка;
- Medium `600–1099 dp`: navigation rail и adaptive grids;
- Expanded `>= 1100 dp`: desktop-like sidebar, двухколоночная Главная и master-detail Настройки.

Adaptive shell владеет safe-drawing insets и навигацией. Главная, Профили, Ноды и Настройки принимают один layout contract, но используют те же `Store`, `Options`, `VpnController`, `ClashMonitor` и `NinetyVpnService`; бизнес-логика не дублируется по размерам окна.

Профили и Ноды используют responsive `LazyVerticalGrid`. Auto/urltest занимает полную строку. Expanded Главная размещает hero слева, а профиль, активную ноду и live-сессию справа. Expanded Настройки используют master-detail, Compact/Medium — drill-down.

Старый отдельный мобильный Settings screen удалён. Полный breakpoint-контракт и real-device smoke matrix описаны в `docs/RESPONSIVE_UI.md`.

## Реализованный custom routing boundary

`core:model` владеет типизированными `RoutingRule`, `RoutingRuleType`, `DomainMatch`, `RoutingRuleAction` и единым sanitizer. UI, persistence и config builder не обмениваются произвольным sing-box JSON.

Поддерживаются:

- domain suffix/exact/keyword;
- IPv4/IPv6 address и CIDR;
- Android `package_name`;
- desktop-only `process_name`;
- действия proxy/direct/block;
- enable/disable и явный порядок приоритета.

`NinetyConfigBuilder` вставляет пользовательские правила после `sniff`/`hijack-dns`, но перед LAN, region и ad-block rules. Неподдерживаемые платформой, отключённые и пустые правила не попадают в config.

На Android 10+ `NinetyVpnService` использует `ConnectivityManager.getConnectionOwnerUid()` и заполняет libbox `ConnectionOwner.androidPackageName`. Для shared UID приоритет имеет пакет, явно присутствующий в активном правиле. Android 8/9 используют procfs fallback; package rules на этих версиях не добавляются в config.

Редактор находится в `Настройки → Маршрутизация`, сохраняет правила через существующий `Options` DataStore path и поддерживает add/edit/delete, reorder и toggle. Полный контракт и smoke matrix описаны в `docs/CUSTOM_ROUTING.md`.

## Реализованный Quality Engine boundary

`:core:quality` принимает завершённый batch задержек, текущую effective node, монотонно переданное время и immutable `QualityPolicy`. Модуль не знает об Android, libbox, Compose или DataStore.

Policy хранит bounded history и вычисляет:

- median latency;
- p90 jitter;
- success rate;
- consecutive failures;
- exponential cooldown;
- health score `0..100`;
- стабильную recommendation с min dwell и switch margin.

Первый batch закрепляет текущую effective node libbox как incumbent. Challenger переключает Auto только после достаточной истории, dwell и материального преимущества. Недоступный incumbent заменяется лучшей доступной нодой без ожидания dwell.

`ClashMonitor` остаётся источником измерений. `QualityRuntime` хранит независимую историю по profile id в versioned `quality_json`, отбрасывает удалённые ноды, не применяет stale recommendation и просит generation-safe reload только при реальном изменении решения.

Пользовательский `ProxySelection.Auto` не заменяется в persistence. На runtime config boundary Auto временно разрешается в конкретную рекомендованную ноду; ручной выбор никогда не меняется Quality Engine.

Экран Ноды показывает последний raw ping отдельно от Q-score, success rate и jitter. Полный scoring contract и device matrix описаны в `docs/QUALITY_ENGINE.md`.

## Platform capabilities

UI и config builder обязаны принимать `PlatformCapabilities`, а не проверять платформу строками или скрытыми условиями.

Android:

- TUN: да;
- per-app routing: Android 10+ через connection owner API;
- domain/IP custom routing: да;
- stable quality Auto: да;
- Always-on/lockdown: через систему Android;
- system proxy: нет;
- WFP kill switch: нет;
- Windows DPI sidecar: нет;
- Naive/TrustTunnel sidecars: пока нет;
- WARP: отдельный этап.

## Проверки до merge

Минимальный gate:

```text
:core:model:test
:core:parser:test
:core:config:test
:core:runtime:test
:core:quality:test
:data:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
```

Для config builder обязательны golden tests на тех же fixtures, что используются parser-слоем и desktop-Ninety. Для Room обязательны закоммиченные schema JSON и явные migrations без destructive fallback. Для runtime обязательны тесты stale completion/failure и реальный smoke-test start/stop/reload/revoke. Для UI обязательна проверка Compact/Medium/Expanded, font scale, тёмной/светлой темы и реальных VPN/import/diagnostics сценариев. Для custom routing обязательны device-проверки первого совпадения, domain/IP actions, package routing Android 10+, persistence и reload активного туннеля. Для Quality Engine обязательны проверки отсутствия флаппинга, dwell/margin, failure cooldown, stale history, ручного выбора и изоляции профилей.

## Этапы

1. ✅ Foundation: `core:model`, capability matrix, design tokens, CI.
2. ✅ Parser: нормализованные DTO, Android adapter и fixtures desktop/Android.
3. ✅ Config: pure Kotlin builder, typed options, shared fixtures и golden JSON.
4. ✅ Data: Room/DataStore, encrypted secrets, verified legacy migration и rollback journal.
5. ✅ Runtime: сериализованная command queue, generation safety и `StateFlow`.
6. ✅ UI: responsive shell, Главная, Профили, Ноды и master-detail Настройки.
7. ✅ Advanced / Custom routing: typed rules, sanitizer, editor, deterministic config и Android package owner.
8. ✅ Advanced / Quality engine: bounded history, health score, cooldown и устойчивый Auto.
9. Следующий — Advanced / WARP integration.
