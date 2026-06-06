<div align="center">

# Ninety · Android

**VPN-клиент 190x4 для Android.** Движок sing-box, нативный интерфейс на Jetpack Compose.

</div>

---

## Возможности

- Туннель на базе sing-box (libbox) поверх Android VpnService
- Импорт подписок и одиночных конфигов (`ninety://`, буфер обмена)
- Список узлов с замером задержки и выбором сервера
- Четыре темы оформления: Kurogane · Synthwave · Matrix · Mono
- Обновления внутри приложения (OTA)

> Ранний доступ. Часть функций десктопной версии (специфичные для Windows) на Android недоступна по архитектуре платформы.

## Сборка

Приложение собирается в GitHub Actions: ядро `libbox.aar` собирается из исходников sing-box через `gomobile`, затем Gradle ассемблит APK. Локальная сборка требует JDK 17 + Android SDK 35 + NDK.

```bash
gradle :app:assembleDebug
```

## Установка

APK публикуется в [Releases](../../releases). Поставьте поверх предыдущей версии — приложение само предложит обновление при выходе новой.

## Лицензия

MIT — см. [LICENSE](LICENSE).
