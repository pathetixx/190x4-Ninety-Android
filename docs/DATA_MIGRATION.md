# Data migration: legacy JSON / SharedPreferences → Room / DataStore

## Цель

Перевести Android-клиент на типизированное и проверяемое хранение без потери уже установленных профилей, нод, активного выбора и настроек.

Новый контур:

```text
Room `ninety.db`
  profiles
  nodes -> profiles.id (FOREIGN KEY, ON DELETE CASCADE)

Preferences DataStore `ninety.preferences_pb`
  theme / update preferences
  active profile / active node / Auto
  VPN options JSON
  migration marker
```

Room schema v1 хранится в:

```text
data/schemas/pw.x4.ninety.data.persistence.NinetyDatabase/1.json
```

Любое изменение `@Entity` после schema v1 требует:

1. увеличить версию `NinetyDatabase`;
2. добавить явную Room migration;
3. закоммитить новую schema;
4. проверить upgrade с предыдущей schema;
5. не использовать destructive migration для пользовательских данных.

## Что шифруется

`AndroidKeystoreSecretCodec` использует AES/GCM с отдельным случайным IV для каждого значения. Ключ `ninety-storage-v1` создаётся и остаётся в Android Keystore.

В Room шифруются:

- URL подписки;
- UUID / credentials;
- пароли;
- obfs password;
- исходная share-ссылка `raw`.

В DataStore шифруется legacy subscription URL. Android backup для приложения уже отключён в manifest.

## Порядок первого запуска

`NinetyApplication` до первой Compose-композиции и до доступа QS/VPN выполняет:

1. читает `nodes.json`, `profiles.json` и SharedPreferences без изменения файлов;
2. восстанавливает старый flat-list формат, если `profiles.json` ещё не существовал;
3. нормализует foreign keys и активный выбор;
4. записывает граф профилей/нод в Room одной транзакцией;
5. читает Room обратно и сравнивает полные расшифрованные записи;
6. записывает настройки в DataStore;
7. читает DataStore обратно и сравнивает snapshot;
8. только после успешной проверки ставит migration marker;
9. передаёт проверенный snapshot старым синхронным facade `Store` / `Prefs`.

Если любой шаг падает, marker не записывается. Приложение запускается на нетронутом legacy snapshot и повторяет миграцию при следующем старте.

## Защита от прерывания процесса

Переходный период использует dual write:

```text
mutation
  -> legacy rollback journal
  -> Room / DataStore
  -> read-back verification
```

### Профили и ноды

`Store` пишет `nodes.json` и `profiles.json` через временный файл, `fsync` и atomic replace. После обоих файлов последним записывается `storage-journal.v1` с SHA-256 каждого payload.

На следующем запуске legacy graph считается пригодным для восстановления Room только когда:

- JSON-файлы читаются;
- оба payload парсятся;
- checksum-манифест совпадает.

Сбой между двумя файловыми операциями оставляет checksum mismatch и не может затереть корректную Room database.

### Настройки

`Prefs` сначала синхронно фиксирует SharedPreferences rollback value, затем пишет DataStore и проверяет read-back. Если процесс был остановлен между этими шагами, следующий startup сравнивает оба snapshot и доводит DataStore до последнего legacy value.

## Почему legacy-файлы пока не удаляются

Удаление старых файлов отложено до отдельного релизного этапа. Они нужны для:

- безопасного повторного запуска миграции;
- восстановления после прерванной записи;
- временного rollback на предыдущий APK во время device validation.

После подтверждённого upgrade smoke-test на реальном устройстве нужен отдельный cleanup milestone. До него нельзя удалять `nodes.json`, `profiles.json`, SharedPreferences `ninety` или checksum-манифест.

## CI gate

До дорогой сборки libbox выполняется отдельный job:

```text
:data:testDebugUnitTest
```

Он проверяет:

- полное преобразование persistence model ↔ Room entity;
- отсутствие открытых credentials в encrypted entity fields;
- round-trip всех чувствительных значений;
- нормализацию orphan/duplicate records;
- сохранение Auto только для непустого активного профиля;
- checksum rollback journal;
- генерацию Room schema artifact.

Полный Android gate дополнительно обязан пройти:

```text
:app:assembleDebug
:app:lintDebug
```

## Ограничение проверки

CI не воспроизводит upgrade уже установленного APK с реальными Android Keystore, SQLite и SharedPreferences пользователя. До снятия draft-статуса обязательно проверить на устройстве:

1. установить предыдущий APK и создать несколько профилей;
2. выбрать обычную ноду и Auto;
3. изменить тему, DNS и routing options;
4. установить новый debug APK поверх без очистки данных;
5. проверить профили, выбор, настройки и запуск VPN;
6. перезапустить процесс и устройство;
7. обновить и удалить профиль, затем снова перезапустить;
8. проверить логи `NinetyStorage`: source, verified, profile/node counts.
