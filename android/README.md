# PurrMatch Android

Экраны и библиотека Jetpack Compose компонентов из страницы Figma
[«Ии макет», 264:5](https://www.figma.com/design/X7Kp2gmSu5nTt3mpFwV7d0/neRacov-meet?node-id=264-5).

- `presentation/src/main/.../components`: переиспользуемые компоненты и тема.
- `presentation/src/main/.../auth`, `browse`, `favorites`, `profile`, `filters`, `listing`: все 16 экранов и два состояния свайпа, сгруппированные по функциональности.
- `presentation/src/main/.../App.kt`: навигация и состояние приложения.
- `presentation/src/debug`: Preview всех экранов и каталог компонентов.
- `app`: приложение для запуска экранов и проверки сценариев на устройстве.
- `design/figma`: исходные SVG и соответствие Android-ресурсам.

Открывайте эту папку в Android Studio. Нужны JDK 17+, Android SDK 37 и
`sdk.dir` в локальном `local.properties` либо `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :presentation:assembleRelease
./gradlew :app:lintDebug :presentation:lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :presentation:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.purrmatch.android/.MainActivity
# Открыть конкретный экран:
adb shell am start -S -n com.purrmatch.android/.MainActivity --es screen home
# Сохранённый каталог компонентов:
adb shell am start -S -n com.purrmatch.android/.MainActivity --es sample catalog
```

Приложение открывается с экрана входа. Это локальный интерактивный прототип:
вход принимает непустые логин и пароль, регистрация проверяет обязательные поля,
длину и повтор пароля. Серверная авторизация и хранение данных между запусками
не подключены. Состояние экранов, профиля, фильтров, избранного и созданных анкет
восстанавливается при пересоздании Activity. Фотографии выбираются системным
Photo Picker, контакты открываются установленными приложениями.

Избранное сохраняет неактуальные анкеты после удаления автором. Свайпы
переключают карточки, правый добавляет в Избранное. Фильтры применяются к
локальному набору анкет; сброс восстанавливает исходные условия. Формы
прокручиваются, основное действие закреплено снизу, системные панели и
клавиатура учитываются в хосте.

## Экраны

| Figma | `screen` | Экран |
| --- | --- | --- |
| 264:7 | `login` | Вход |
| 264:65 | `registration` | Регистрация |
| 264:111 | `complete-profile` | Заполнение профиля |
| 264:721 | `home` | Главная |
| 264:780 | `swipe-right` | Свайп вправо |
| 264:842 | `swipe-left` | Свайп влево |
| 264:573 | `filters` | Фильтры |
| 264:662 | `no-results` | Нет новых анкет |
| 264:907 | `listing` | Полная анкета |
| 264:398 | `favorites` | Избранное |
| 264:541 | `unavailable` | Неактуальная анкета |
| 264:483 | `empty-favorites` | Пустое Избранное |
| 264:159 | `profile` | Мой профиль |
| 264:259 | `new-listing` | Новая анкета |
| 264:364 | `delete` | Подтверждение удаления |
| 264:1068 | `settings` | Настройки |
| 264:1013 | `author` | Публичный профиль |
| 264:1137 | `logout` | Подтверждение выхода |

[API компонентов](presentation/README.md). `App` связывает экраны;
отдельные composable принимают данные и обработчики, не зависят от сети и ViewModel.

`ScreensTest` проверяет пользовательские сценарии и снимает все 18
состояний при размере 376×803 dp. PNG сохраняются в
`presentation/build/outputs/connected_android_test_additional_output`.
Снимки диалогов включают отдельное окно Android. Рамка телефона и условный
статус-бар Figma служат оформлением макета; на устройстве используются настоящие
системные панели. В размерах и отступах за основу принят контент макета 376×803;
на других размерах области растягиваются, длинный контент прокручивается.
