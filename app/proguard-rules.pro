# Наш код: enum.valueOf/.name по строкам Prefs, ручной org.json — обфускация
# сломала бы молча (сброс темы/настроек). Держим пакет целиком.
-keep class pw.x4.ninety.** { *; }

# libbox (gomobile) — gobind-классы зовутся по имени из натива.
-keep class io.nekohasekai.libbox.** { *; }
-keep class go.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
