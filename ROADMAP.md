# TaskFlow Android Roadmap

Клиент развивается отдельно от `../web`, но сохраняет совместимость с его стабильным API `/api/v1` версии `v0.2.0`.

Обозначения: `[x]` готово, `[~]` в работе, `[ ]` запланировано.

## Принципы

1. Локальная база - источник интерфейса; сеть только синхронизирует её.
2. Создание, изменение и удаление задач сначала записываются локально и получают client mutation ID.
3. Конфликты никогда не перезаписываются молча: пользователь выбирает серверную или локальную версию.
4. Визуальная система повторяет TaskFlow Web: светлая и тёмная темы, нейтральные поверхности, фиолетовый акцент, компактные карточки и ясные состояния.
5. API-контракт берётся из `../web/docs/openapi.json`; изменения контракта сначала реализуются и тестируются в web-репозитории.

## 0.3.0 - Android beta

Цель: ежедневная работа с задачами при нестабильном или отсутствующем подключении.

### Основа приложения

- [x] Gradle-проект Kotlin + Jetpack Compose.
- [x] Базовая дизайн-система TaskFlow: темы, типографика, формы, интервалы и компоненты состояний.
- [x] Адаптивный app shell с нижней навигацией для телефона и navigation rail для широкого экрана.
- [x] Навигация между «Сегодня», «Входящие», «Проекты» и «Ещё» через adaptive app shell.
- [ ] Dependency injection и разделение `data` / `domain` / `feature`.

### Данные и доступ

- [x] Retrofit/Moshi-контракт для login, refresh, `/sync` и task mutation batch по OpenAPI.
- [x] Безопасное хранение access и rotating refresh token через Android Keystore.
- [x] Login, восстановление локальной сессии, регистрация и email verification через `taskflow://verify` link.
- [x] Room-модель задач, проектов, колонок, durable outbox и sync-курсора.
- [x] Версионированная миграция Room v1-to-v2 без удаления задач и durable outbox, проверена instrumented-тестом.

### Offline-first синхронизация

- [x] Durable outbox и пакетная отправка idempotent task mutations при запуске и ручном refresh с последующим pull sync.
- [x] WorkManager: unique network job, экспоненциальный retry, token refresh, пакетная отправка и pull sync после локальных изменений.
- [x] Pull sync задач, проектов и колонок: пагинация `snapshot` / `next_cursor`, sync-курсор и tombstone-удаления.
- [x] Экран и сценарий явного разрешения version conflict: выбор серверной версии или повтор локальной с актуальной версией.
- [x] Инструментационные тесты offline create/update/delete, idempotent mutation retry, server-version conflict, pagination и refresh проходят на AVD.

### Пользовательские сценарии beta

- [~] Экраны «Сегодня», «Входящие» и read-only «Проекты» из Room; редактор задачи поддерживает описание, проект, дату и срок.
- [x] Offline-создание, редактирование заголовка/приоритета, завершение и удаление задач с durable outbox.
- [x] Локальный фильтр задач по проекту, статусу и приоритету.
- [x] Deep links `taskflow://task/{id}` и action «Поделиться в TaskFlow» для текста.
- [x] Локальные уведомления о сроках через WorkManager после сохранения `due_at`.

### Beta gate

- [~] Unit-тест retry policy и instrumented data-тесты проходят; domain-слой пока не выделен.
- [x] Compose UI-тесты покрывают вход, offline-создание задачи и оба варианта разрешения конфликта.
- [x] Проверены светлая/тёмная тема, телефон и широкий экран на AVD.
- [x] Проверена совместимость с чистым сервером TaskFlow `v0.2.0`: login, pull sync и offline mutation push; opt-in instrumented E2E запускается с внешними runner arguments.
- [x] Debug APK `0.3.0-beta.1` собирается, запускается на AVD; tracked keys и secrets не найдены.
