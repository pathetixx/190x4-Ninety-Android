# Ninety Android rewrite architecture

## Цель

Переписать Android-клиент вокруг проверенного `VpnService + libbox`, сохранив общие продуктовые правила desktop-Ninety и отдельные platform adapters.

Кроссплатформенный контракт:

- единые модели, parser/config fixtures и routing semantics;
- pure Kotlin policy modules;
- отдельные Windows/Android runtime effects;
- нативный Android lifecycle и responsive Compose UI.

## Модули

```text
:core:model
  ProxyNode/Profile/Selection/Capabilities/RoutingRule/Warp models

:core:parser
  share links + subscription normalization

:core:config
  deterministic sing-box JSON + optional WARP decoration

:core:runtime
  commands, phases and generation-safe transitions

:core:quality
  bounded history, health score, cooldown and stable Auto

:data
  Room, DataStore, migration, rollback and encrypted secrets

:app
  Compose, Android adapters, VpnService, libbox and Cloudflare registration
```

Core-модули не импортируют Android, Compose, libbox или конкретное хранилище.

## Parser boundary

`core:model` владеет нормализованными `ProxyNode`/`ProxyProtocol`. `core:parser` поддерживает VLESS, VMess, Trojan, Shadowsocks, Hysteria2 и TUIC, включая Reality, IPv6, xHTTP, plugins, pins и subscription bodies. Android `LinkParser` остаётся compatibility adapter.

## Config boundary

`core:config` принимает `ConfigNode`, `ProxySelection`, immutable `SingBoxOptions` и путь лога. Он детерминированно строит sing-box 1.13 JSON и покрыт golden tests для протоколов, DNS/FakeDNS, regional routes, custom routing, TLS tricks, mux и WARP.

Android `ConfigBuilder` только переводит legacy `Node`/`Options.Data` в core DTO.

## Data boundary

`:data` владеет:

- Room `ninety.db`, profiles/nodes и cascade delete;
- Preferences DataStore;
- verified legacy migration;
- rollback journal;
- AES/GCM Android Keystore codec;
- отдельным encrypted WARP store.

`Store`, `Prefs` и `Options` остаются временными синхронными facade. WARP private key, access token и license не входят в `optionsJson`, Room или quality history.

## Runtime boundary

`:core:runtime` владеет `Start`, `Reload`, `Stop`, фазами и generation token. Android выполняет platform effects одной FIFO-очередью. Поздняя старая операция не может опубликовать Connected или остановить новый запуск.

`CommandServer`, in-flight server, TUN fd и network callback закрываются через одну resource boundary. `VpnController` публикует `StateFlow<VpnSnapshot>`.

## Responsive UI boundary

Единые классы окна:

- Compact `<600dp`: bottom navigation;
- Medium `600–1099dp`: rail и adaptive grids;
- Expanded `>=1100dp`: sidebar, split Home и master-detail Settings.

Главная, Профили, Ноды и Настройки используют одни data/runtime API. Полный контракт: `docs/RESPONSIVE_UI.md`.

## Custom routing boundary

`core:model` владеет typed `RoutingRule`, domain/IP/package/process types, suffix/exact/keyword и proxy/direct/block actions. Единый sanitizer используется UI, persistence и config.

Правила вставляются после sniff/DNS hijack, но перед LAN/region/ad-block. Android 10+ использует connection-owner API для `package_name`; Android 8/9 сохраняют domain/IP rules без package matching. Контракт: `docs/CUSTOM_ROUTING.md`.

## Quality Engine boundary

`:core:quality` хранит bounded history, median/p90 jitter, success rate, failures, cooldown и score `0..100`. Первый batch закрепляет effective node, challenger переключает Auto только после history+dwell+margin, недоступный incumbent заменяется сразу.

История разделена по profile id и хранится в `quality_json`. Stale recommendation не применяется. Direct WARP не записывает quality batches и не вызывает proxy reload; Chain продолжает использовать устойчивый Auto. Контракт: `docs/QUALITY_ENGINE.md`.

## WARP boundary

Typed WARP модели разделяют:

- публичные настройки: enabled, Direct/Chain, endpoint, MTU, noise;
- секретную регистрацию: private key, token, license, peer and interface data.

Android использует локально сгенерированный WireGuard keypair и транзакционную Cloudflare регистрацию:

```text
new keypair -> remote registration -> optional license activation
            -> validation -> encrypted atomic commit -> old remote cleanup
```

При ошибке provisional registration удаляется best effort, предыдущая локальная регистрация остаётся рабочей.

`NinetyConfigBuilder` добавляет root `endpoints` WireGuard tag `warp`:

- Direct: без detour;
- Chain: `detour: proxy`;
- route final, remote DNS, rule-set downloads и custom Proxy action направляются в WARP;
- explicit Direct/Block/LAN/region rules сохраняют приоритет.

WARP UI находится в `Настройки → Маршрутизация`. Endpoint scanner/deep scan отложены. Полный контракт: `docs/WARP.md`.

## Platform capabilities

Android:

- TUN: да;
- per-app routing: Android 10+;
- custom domain/IP routing: да;
- stable quality Auto: да;
- WARP Direct/Chain: да;
- Always-on/lockdown: через Android;
- system proxy/WFP kill switch/Windows sidecars: нет.

## Gate до merge

```text
:core:model:test
:core:parser:test
:core:config:test
:core:runtime:test
:core:quality:test
:data:testDebugUnitTest
:app:assembleDebug
:app:lintDebug
```

CI не заменяет real-device tests. Обязательны upgrade migration, start/stop/reload/revoke, responsive matrix, routing first-match/package owner, Quality hysteresis/cooldown и полный WARP registration/Direct/Chain/reset/network-failure smoke-test.

## Этапы

1. ✅ Foundation.
2. ✅ Parser.
3. ✅ Config.
4. ✅ Data migration and encrypted persistence.
5. ✅ Serialized runtime.
6. ✅ Responsive UI.
7. ✅ Advanced / Custom routing.
8. ✅ Advanced / Quality Engine.
9. ✅ Advanced / WARP Direct/Chain integration.

Следующий этап определяется после real-device smoke review; PR остаётся draft.
