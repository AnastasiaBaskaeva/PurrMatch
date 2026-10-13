# PurrMatch

Монорепозиторий мобильного приложения и бэкенда.

## Структура

- `mobile/` — мобильный проект; Android CI ожидает `mobile/gradlew` и модуль `app`.
- `backend/` — серверный проект.
- `contracts/` — общий контракт API.

Проекты пока не добавлены. Android CI использует JDK 17; при выборе Android Gradle Plugin нужно согласовать с ним версию JDK.

## Ветки

- `android/<название>` — рабочие Android-ветки, PR в `android/trunk`.
- `backend/<название>` — рабочие серверные ветки, PR в `backend/trunk`.
- `android/trunk` и `backend/trunk` — релизные ветки соответствующих частей проекта.
- `main` — изменения только через PR из `android/*` или `backend/*`, включая релизные ветки. Прямой push запрещён защитой ветки GitHub.

Названия проверяются для PR в `main`, `android/trunk` и `backend/trunk`. GitHub Actions не запрещает само создание веток с другими именами.

## CI

Для каждого PR в `android/trunk`, а также при его обновлении, повторном открытии или изменении целевой ветки выполняется из `mobile/`:

```sh
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

До добавления Android-проекта проверка завершится ошибкой с пояснением. Backend workflow для PR в `backend/trunk` пока только выводит сообщение; сборка и тесты не настроены.

Workflow `Branch policy` проверяет соответствие префикса исходной ветки целевой ветке. Фильтрации по изменённым файлам нет.

## Защита main в GitHub

Файл `.github/main-protection.json` описывает запрет прямого push, force push и удаления `main`, включая администраторов. Для слияния обязателен статус `Branch policy`; стороннее одобрение PR не требуется.

GitHub не применяет этот JSON автоматически. После установки GitHub CLI и авторизации администратора репозитория настройку можно применить из корня:

```sh
gh api --method PUT repos/AnastasiaBaskaeva/PurrMatch/branches/main/protection \
  --input .github/main-protection.json
```

Перед применением к репозиторию с существующей защитой нужно сверить её настройки: запрос заменяет перечисленные параметры.

Первоначальную конфигурацию следует влить через PR в `main`, затем создать `android/trunk` и `backend/trunk` от обновлённой `main`. Это обеспечит наличие workflows в обеих релизных ветках.
