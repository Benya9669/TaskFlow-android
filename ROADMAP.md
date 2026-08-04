# TaskFlow Android Roadmap

Android-клиент развивается отдельно от `../web` и использует стабильный API `/api/v1`. Текущий продуктовый статус: **0.3.0-alpha**. Технический offline foundation и test gate готовы, но UI/UX и функциональное покрытие ещё не соответствуют публичной beta.

Обозначения: `[x]` готово и проверено, `[~]` реализовано частично, `[ ]` запланировано.

## Принципы

1. Room остаётся источником UI; сеть только синхронизирует локальное состояние.
2. Любое действие с задачей сначала сохраняется локально и попадает в durable outbox с client mutation ID.
3. Конфликты не перезаписываются молча: пользователь выбирает серверную или локальную версию.
4. Приоритет развития: ежедневный task flow на телефоне, затем feature parity с web, затем расширенная архитектура.
5. Новые offline-сущности добавляются вместе с версионированным web API, outbox-операциями, migration и E2E-тестом.

## Реализовано: 0.3.0-alpha foundation

### Приложение и UI

- [x] Kotlin, Jetpack Compose, Material 3 design system, light/dark theme.
- [x] Adaptive shell: bottom navigation на телефоне и navigation rail при ширине от 720dp.
- [x] Разделы «Сегодня», «Входящие», «Проекты», «Ещё».
- [x] «Сегодня» показывает задачи на текущую дату; «Входящие» поддерживают быстрый offline create.
- [x] Список задач: завершение, удаление, редактирование названия, описания, приоритета, проекта, даты и срока.
- [x] Локальные фильтры по проекту, статусу и приоритету.
- [x] Read-only список проектов с количеством задач.
- [x] Empty, loading и error состояния для существующих экранов.
- [x] Deep links `taskflow://task/{id}`, `taskflow://verify`, Android share text intent.
- [x] Локальные reminders по `due_at` через WorkManager.

### Данные и синхронизация

- [x] Retrofit/Moshi контракт login, register, verify email, refresh, sync и task mutation batch.
- [x] Access и rotating refresh token в EncryptedSharedPreferences с Android Keystore.
- [x] Room: tasks, projects, kanban columns, durable outbox, sync cursor и version conflicts.
- [x] Room migration v1-to-v2 без потери задач и outbox.
- [x] Offline create, update, complete и delete задач; durable idempotent retry.
- [x] Pull sync с pagination, snapshot/cursor, tombstones проектов, колонок и задач.
- [x] WorkManager network sync, exponential retry и refresh token.
- [x] Явное разрешение version conflict: принять серверную или повторить локальную версию.

### Проверки foundation

- [x] Instrumented data tests: offline CRUD, retry, conflict, pagination, refresh и migration.
- [x] Compose UI tests: login, offline inbox create, обе ветки conflict dialog.
- [x] Проверены light/dark theme, phone и wide layout на AVD.
- [x] Проверен Android login, pull и task mutation push с чистым web `v0.2.0`.
- [x] Debug APK `0.3.0-beta.1` собирается и запускается; tracked secrets и ключи не найдены.

## 0.3.0 — ежедневный task flow

Цель: Android становится удобным самостоятельным приложением для ежедневного ведения личных задач, а не демонстрацией sync layer.

### Главные сценарии

- [ ] Переработать «Сегодня» и «Входящие»: группировка, ясные счётчики, completed section, pull-to-refresh и feedback после sync.
- [ ] Заменить текстовые ISO-поля даты и срока на Material date/time pickers, добавить clear date и overdue presentation.
- [ ] Улучшить быстрый ввод: autofocus, submit с IME, быстрый выбор приоритета/даты и snackbar с Undo после delete/complete.
- [ ] Сделать task detail/editor полноценным bottom sheet или отдельным экраном с валидацией, сохранением и unsaved-change handling.
- [ ] Реализовать поиск задач по title и description в локальной базе.
- [ ] Сделать «Проекты» продуктовым экраном: active/archive tabs, task counts, цвет и переход к отфильтрованному списку.
- [ ] Сделать «Ещё» рабочим разделом: профиль, сервер, sync status, последняя синхронизация, manual refresh, logout и app version.
- [ ] Добавить понятные offline/sync/error индикаторы в app shell.

### Поддержка API и данных

- [ ] Добавить в web API и Android create/update/archive/restore проектов с version guard и durable project outbox.
- [ ] Добавить Room migration и conflict policy для project mutations.
- [ ] Покрыть project API contract и Android E2E на чистом совместимом server tag.

### Gate 0.3.0

- [ ] Пользователь проходит первый запуск, login или registration/verification и создаёт первую задачу без неясных состояний.
- [ ] Offline create/edit/complete/delete, undo и последующий sync проверены вручную и instrumented-тестами.
- [ ] Сегодня, входящие, проекты и настройки имеют завершённые empty/loading/error/offline состояния.
- [ ] Manual UX review на phone и wide layout в light/dark theme.

## 0.4.0 — рабочее пространство и parity с web

Цель: пользователь может вести основные сущности TaskFlow с Android без возврата в web для обычной работы.

- [ ] Kanban board по статусам, перемещение задач между колонками и project filter.
- [ ] Создание и редактирование проектов, archive/restore, выбор project color.
- [ ] Subtasks и checklist items с offline sync.
- [ ] Notes, folders и links с offline-first model.
- [ ] Task discussions/messages и activity history.
- [ ] Recurrence, estimated time, tags и расширенные filters.
- [ ] Search results across tasks, projects и notes.
- [ ] Notification preferences, reminder offsets и notification channels.
- [ ] Export/import или безопасный handoff к web export flow.

### Gate 0.4.0

- [ ] Каждая добавленная сущность имеет migration, sync, conflict policy, offline UI и E2E coverage.
- [ ] Основные web рабочие сценарии воспроизводимы на Android без потери данных.
- [ ] Accessibility review: TalkBack labels, focus order, touch targets и dynamic font scale.

## 0.5.0 — reliability, architecture и public beta

Цель: стабильная публичная beta, пригодная для регулярного использования и распространения среди тестовых пользователей.

- [ ] Внедрить DI и разделить `data` / `domain` / `feature` без изменения поведения.
- [ ] Выделить use cases и добавить unit tests для task, project, sync и conflict policy.
- [ ] Добавить structured diagnostics для sync failures без записи tokens, личных данных и task content.
- [ ] Настроить CI: lint, unit tests, instrumented tests и compatibility E2E against supported web version.
- [ ] Добавить baseline profile, startup/performance checks и контроль размера APK.
- [ ] Добавить crash reporting с privacy-safe opt-in и release monitoring.
- [ ] Подготовить signed release build, versioning, changelog, privacy policy и beta distribution.
- [ ] Провести ручную regression matrix: clean install, upgrade, offline, airplane mode, token expiry, conflict, rotation и restore.

### Public Beta Gate

- [ ] Все пункты `0.3.0` завершены и прошли UX review.
- [ ] Нет blocker/crash в основного task flow на поддерживаемых Android версиях.
- [ ] Автоматические tests и supported-server compatibility E2E green в CI.
- [ ] Подписанный APK/AAB собран без секретов; есть install, update и rollback инструкции.
- [ ] Ограничения beta и channel обратной связи описаны для тестовых пользователей.

## После public beta

- [ ] Tablet/multi-window workflow и foldable layouts.
- [ ] Home screen widgets и quick settings shortcuts.
- [ ] Android share target для ссылок, файлов и selected text с preview.
- [ ] Offline attachments после появления server-side attachment API.
- [ ] Wear OS, calendar integration и automation integrations после отдельной product validation.
