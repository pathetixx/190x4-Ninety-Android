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

:data
  Room, DataStore, migrations, encrypted secrets

:vpn:libbox
  VpnService, PlatformInterface, CommandServer/Client

:app
  Compose, ViewModel, navigation, Android intents
```

## Правила зависимостей

```text
app -> data -> core:model
app -> core:parser -> core:model
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

## VPN lifecycle

Текущие ручные `Thread` должны быть заменены одним сериализованным actor/command queue:

```text
Start(selection)
Stop(reason)
Reload(selection/options revision)
NetworkChanged(network)
PermissionRevoked
```

Одновременно выполняется только одна команда. Каждая долгая операция получает generation token; устаревший результат не может изменить новое состояние.

## Проверки до merge

Минимальный gate:

```text
:core:model:test
:core:parser:test
:core:config:test
:data:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
```

Для config builder обязательны golden tests на тех же fixtures, что используются parser-слоем и desktop-Ninety. Для Room обязательны закоммиченные schema JSON и явные migrations без destructive fallback.

## Этапы

1. ✅ Foundation: `core:model`, capability matrix, design tokens, CI.
2. ✅ Parser: нормализованные DTO, Android adapter и fixtures desktop/Android.
3. ✅ Config: pure Kotlin builder, typed options, shared fixtures и golden JSON.
4. ✅ Data: Room/DataStore, encrypted secrets, verified legacy migration и rollback journal.
5. Следующий — Runtime: сериализованный VPN lifecycle и StateFlow.
6. UI: responsive desktop design language в Compose.
7. Advanced: custom routing, quality engine, WARP.
