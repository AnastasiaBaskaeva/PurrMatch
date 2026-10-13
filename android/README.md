# PurrMatch Android

Пустой Android-проект на Kotlin с одной Activity без интерфейса.

## Требования

- JDK 17.
- Android SDK Platform 36 и Build Tools 36.0.0.
- Путь к SDK в `ANDROID_HOME` или в локальном `local.properties` (`sdk.dir=...`).

Откройте эту папку как проект в Android Studio. Gradle запускается через
включённый в репозиторий Wrapper.

## Сборка и проверки

Из папки `android`:

```sh
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Это те же проверки, которые выполняет Android CI для PR в `android/trunk`.
Исходная ветка PR должна начинаться с `android/`, согласно Branch policy.
Unit-тестов пока нет: задача `testDebugUnitTest` завершается с `NO-SOURCE`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
