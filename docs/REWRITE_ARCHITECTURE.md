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
  ProxyNode/Profile/Selection/Options/Capabilities

:core:parser
  share links + subscription normalization

:core:config
  deterministic sing-box JSON builder

:core:runtime
  VPN commands, phases, generation-safe transitions

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
- Preferences DataStore для настроек и активного выбора;
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

## Platform capabilities

UI и config builder обязаны принимать `PlatformCapabilities`, а не проверять платформу строками или скрытыми условиями.

Android:

- TUN: да;
- per-app routing: да;
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
:data:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
```

Для config builder обязательны golden tests на тех же fixtures, что используются parser-слоем и desktop-Ninety. Для Room обязательны закоммиченные schema JSON и явные migrations без destructive fallback. Для runtime обязательны тесты stale completion/failure и реальный smoke-test start/stop/reload/revoke. Для UI обязательна проверка Compact/Medium/Expanded, font scale, тёмной/светлой темы и реальных VPN/import/diagnostics сценариев.

## Этапы

1. ✅ Foundation: `core:model`, capability matrix, design tokens, CI.
2. ✅ Parser: нормализованные DTO, Android adapter и fixtures desktop/Android.
3. ✅ Config: pure Kotlin builder, typed options, shared fixtures и golden JSON.
4. ✅ Data: Room/DataStore, encrypted secrets, verified legacy migration и rollback journal.
5. ✅ Runtime: сериализованная command queue, generation safety и `StateFlow`.
6. ✅ UI: responsive shell, Главная, Профили, Ноды и master-detail Настройки.
7. Следующий — Advanced: custom routing, quality engine и WARP.
