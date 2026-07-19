# VPN runtime: serialized command queue + generation safety

## Цель

Исключить гонки между `start`, `reload`, `stop`, системным revoke и callback'ами libbox. Раньше запуск и reload выполнялись в независимых `Thread`: позднее завершение старого запуска могло вернуть UI в `Connected` уже после команды отключения или применить устаревший выбор ноды.

## Граница ответственности

Pure Kotlin модуль `:core:runtime` владеет только:

- командами `Start`, `Reload`, `Stop(error)`;
- фазами `Idle`, `Starting`, `Connected`, `Reloading`, `Stopping`;
- монотонным `generation`;
- проверкой актуальности completion/failure;
- детерминированными переходами, покрытыми JVM-тестами.

Модуль не импортирует Android, Compose, libbox или хранилище.

Android `NinetyVpnService` владеет platform effects:

- foreground notification;
- построением sing-box config;
- `Libbox.setup/checkConfig`;
- `CommandServer`;
- TUN file descriptor;
- network callback;
- остановкой foreground service.

`VpnController` публикует единый `StateFlow<VpnSnapshot>`. Старые Compose-экраны временно получают совместимый state mirror, но runtime source of truth — только `StateFlow`.

## Очередь

Все platform effects выполняются одним `newSingleThreadExecutor`:

```text
Android intent / libbox callback
        │
        ├─ stateMachine.request(command)  // generation меняется сразу
        │
        └─ FIFO runtime executor
              ├─ Start(ticket)
              ├─ Reload(ticket)
              └─ Stop(ticket)
```

Ticket создаётся до постановки в очередь. Поэтому новая команда немедленно инвалидирует уже выполняющуюся долгую операцию. Корректность не зависит от `Thread.interrupt()` и от того, умеет ли libbox отменять текущий native call.

## Инварианты

1. Только ticket текущего generation может опубликовать `Connected`, `Idle` или ошибку.
2. `Stop` во время `Start` не позволяет позднему start-completion оживить туннель.
3. `Reload` во время `Start` инвалидирует старый config и запускает последний снимок выбора/настроек.
4. Повторные `Stop` безопасны: терминальное состояние принадлежит последнему ticket.
5. Повторный `Start` в `Starting`, `Connected` или `Reloading` игнорируется.
6. Устаревшая ошибка не может остановить более новый запуск.
7. `CommandServer`, in-flight server, TUN fd и network callback закрываются идемпотентно через одну resource boundary.
8. Internal callbacks `serviceStop/serviceReload` принимаются только из стабильного `Connected`, чтобы cleanup старого сервера не создавал новую команду поверх актуального generation.

## Переходы

| Текущее состояние | Команда | Следующее состояние | Примечание |
|---|---|---|---|
| Idle | Start | Starting | создаётся новый generation |
| Starting | Start | без изменений | duplicate игнорируется |
| Starting | Reload | Starting | старый start становится stale |
| Starting | Stop | Stopping | start completion больше невалиден |
| Connected | Reload | Reloading | применяется последний Store/Options snapshot |
| Connected | Stop | Stopping | ресурсы закрываются в FIFO-очереди |
| Reloading | Reload | Reloading | предыдущий reload становится stale |
| Reloading | Stop | Stopping | reload completion игнорируется |
| Stopping | Start | Starting | новый запуск может следовать за queued cleanup |
| Stopping | Stop | Stopping | последний stop владеет terminal state |

## StateFlow

`VpnSnapshot` содержит:

- `state`;
- `generation`;
- `activeServer`;
- `lastError`.

Для UI `Reloading` отображается как `Connecting`. `MainActivity` и QS tile используют синхронные getters поверх того же snapshot; Compose получает автоматическую перерисовку через переходный mirror.

## Автоматические проверки

`:core:runtime:test` фиксирует:

- start → stop → stale connected;
- start → reload → stale old config;
- repeated stop;
- duplicate start;
- stale failure после нового start.

Тесты запускаются в fail-fast core job до дорогой сборки `libbox.aar`.

## Smoke-test на устройстве

Перед снятием draft обязательны:

1. быстро нажать подключить → отключить во время `Connecting`;
2. подключить и несколько раз быстро сменить ноду;
3. сменить профиль во время `Connecting`;
4. нажать stop несколько раз из UI, notification и QS tile;
5. отозвать VPN permission системой;
6. включить auto-connect, убить процесс и открыть приложение;
7. убедиться, что notification, QS tile и главный экран всегда показывают одно состояние;
8. проверить отсутствие зависшего TUN/ключика VPN после остановки.

## Следующий этап

После подтверждения runtime APK на реальном устройстве можно переносить responsive UI без риска, что новые экраны будут маскировать lifecycle-гонки старого сервиса.
