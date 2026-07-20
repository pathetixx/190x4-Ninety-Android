<div align="center">

# Ninety · Android

**Нативный VPN-клиент 190x4 для Android.** Движок sing-box/libbox, интерфейс на Jetpack Compose и визуальная система desktop Ninety.

</div>

---

## Возможности

- Android `VpnService` поверх закреплённой сборки sing-box/libbox
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC, Reality, IPv6 и xHTTP
- Профили-подписки и одиночные конфиги с HTTPS-импортом
- Ноды, ручной выбор и устойчивый Auto с историей качества, hysteresis и cooldown
- Custom routing по доменам, IP и Android-приложениям
- Cloudflare WARP Direct и Chain
- 16 тем desktop Ninety и адаптивный Compose UI для телефонов, планшетов и больших экранов
- OTA-обновления с точным ABI, ограничением размера и проверкой SHA-256
- Зашифрованное хранение секретов через Android Keystore

> Часть Windows-специфичных возможностей desktop Ninety — WFP, system proxy и Windows sidecars — архитектурно недоступна на Android.

## Безопасность данных

После подтверждённой миграции профили и ноды хранятся в Room, чувствительные поля шифруются AES-GCM ключом Android Keystore, а старые plaintext JSON/SharedPreferences удаляются. WARP-регистрация хранится отдельным зашифрованным атомарным файлом.

Импорт сетевых подписок разрешён только через HTTPS и ограничен 10 МБ. Экспортируемая диагностика автоматически удаляет share-ссылки, UUID, пароли, токены, лицензии и query-параметры URL.

## Сборка и проверки

GitHub Actions сначала запускает pure Kotlin и data-тесты, затем собирает закреплённый `libbox.aar`, выполняет app unit tests, debug APK assembly и Android lint. Теги дополнительно проходят release assembly/lint; для каждого APK публикуется файл `.sha256`.

Локальная сборка требует JDK 17, Android SDK 35 и NDK 28:

```bash
gradle :core:model:test :core:parser:test :core:config:test \
  :core:runtime:test :core:quality:test :data:testDebugUnitTest \
  :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## Установка

APK публикуются в [Releases](../../releases) отдельно для `arm64-v8a` и `armeabi-v7a`. Для обновления поверх установленной версии Android-подпись должна совпадать; встроенный OTA также проверяет SHA-256 соответствующего APK.

## Документация

- [Архитектура rewrite](docs/REWRITE_ARCHITECTURE.md)
- [Custom routing](docs/CUSTOM_ROUTING.md)
- [Quality Engine](docs/QUALITY_ENGINE.md)
- [WARP](docs/WARP.md)
- [Responsive UI](docs/RESPONSIVE_UI.md)

## Лицензия

MIT — см. [LICENSE](LICENSE).
