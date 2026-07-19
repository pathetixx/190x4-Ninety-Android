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
  Node/Profile/Selection/Options/Capabilities

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
app -> vpn:libbox -> core:config -> core:model
core:parser -> core:model
```

Core-модули не должны импортировать:

- `android.*`;
- Compose runtime;
- `io.nekohasekai.libbox`;
- конкретное хранилище;
- UI-строки.

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

## Persist

Переход выполняется без потери текущих установок:

1. прочитать legacy `nodes.json`, `profiles.json`, SharedPreferences;
2. нормализовать данные текущим parser;
3. записать в Room транзакцией;
4. сохранить migration marker;
5. legacy-файлы удалить только после успешной проверки новой базы.

Секретные поля и subscription URL не должны храниться в открытом backup.

## Проверки до merge

Минимальный gate:

```text
:core:model:test
:core:parser:test
:core:config:test
:app:lintDebug
:app:assembleDebug
```

Для config builder обязательны golden tests на тех же fixtures, что используются desktop-Ninety.

## Этапы

1. Foundation: `core:model`, capability matrix, design tokens, CI.
2. Parser: нормализованные DTO и fixtures desktop/Android.
3. Config: единый контракт и golden JSON.
4. Data: Room/DataStore + legacy migration.
5. Runtime: сериализованный VPN lifecycle и StateFlow.
6. UI: responsive desktop design language в Compose.
7. Advanced: custom routing, quality engine, WARP.
