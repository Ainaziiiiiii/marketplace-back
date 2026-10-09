# Грин Эко Молл

Мультивендорный B2B2C маркетплейс: магазины-партнёры, внешние и MLM-клиенты, курьеры,
онлайн-оплата с распределением по кошелькам, гео-фильтрация каталога.

**Стек:** Java 21, Spring Boot 4.1.1, Spring Data JPA, PostgreSQL, Flyway, Docker.
Одно приложение (модульный монолит), один порт, одна база.

## Структура

```
src/main/java/greenecomall/
  GreenEcoMallApplication   точка входа
  config/                   общие бины, миграции по схемам, JPA-репозитории
  common/                   общий код: домен, события и шина, безопасность, веб-слой
  auth/ catalog/ order/ finance/ courier/ mlm/ notification/   модули
src/main/resources/
  application.yml           вся конфигурация
  db/migration/<схема>/     миграции Flyway модуля
infra/                      docker-compose (инфра и полный прогон), mock-mlm
docs/                       архитектура и задания
```

| Модуль | Схема БД | Ответственность |
|---|---|---|
| auth | auth | учётки, роли, JWT+refresh, OTP, SSO из MLM |
| catalog | catalog | магазины, товары, категории, гео, наценка, витрина |
| order | orders | корзина, заказы, suborders, статусы, промокоды |
| finance | finance | эквайринг, кошельки, ledger, выводы |
| courier | courier | курьеры, подбор, статусы доставки |
| mlm | mlm | MLM-аккаунты, активация, рефералы, интеграция с MLM-бэком |
| notification | notification | push/SMS/Telegram уведомления |

Всё API — на одном порту под `/api` (`http://localhost:8080/api/...`), healthcheck — `/api/actuator/health`.

**Как модули общаются:**
- синхронно — прямыми вызовами бинов соседнего модуля (каждый вызов в своей транзакции, как раньше по HTTP);
- асинхронно — доменными событиями: модуль пишет событие в свою таблицу `outbox` в одной транзакции
  с данными, `OutboxRelay` досылает его в `InProcessEventBus`, подписчики (`@TopicListener`) обрабатывают
  каждый в своей транзакции, идемпотентно по `eventId`. Kafka не нужна.

## Локальный запуск

```bash
# 1. инфраструктура: postgres:5432 (база gem), mailhog:8025, mock-mlm:9100
docker compose -f infra/docker-compose.infra.yml up -d

# 2. приложение из IDE (GreenEcoMallApplication) или:
./mvnw spring-boot:run

# всё в контейнерах:
docker compose -f infra/docker-compose.yml up -d --build
```

Схемы модулей и таблицы создаёт Flyway при старте. Первый админ — переменные `ADMIN_EMAIL`/`ADMIN_PASSWORD`.

Тесты: `./mvnw test` (интеграционные `*IntegrationTest` поднимают Postgres через Testcontainers — нужен Docker).

## Документы

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — домены, схема БД, события,
  SSO-контракт с MLM, риски и решения по открытым вопросам.
- [docs/DEPLOY-RAILWAY.md](docs/DEPLOY-RAILWAY.md) — деплой.
- [docs/TASK-01-auth-service.md](docs/TASK-01-auth-service.md), [docs/TASK-02-catalog-geo.md](docs/TASK-02-catalog-geo.md) — задания по модулям.
