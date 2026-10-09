# Деплой на Railway

Приложение одно: один сервис Railway, один Dockerfile в корне репозитория, одна база PostgreSQL.

## 1. Что создать в проекте Railway

- **PostgreSQL** — одна база. Схемы модулей (`auth`, `catalog`, `orders`, `finance`, `courier`,
  `mlm`, `notification`) и таблицы создаёт Flyway при первом старте.
- **Сервис приложения** из этого GitHub-репозитория.

Kafka и отдельный api-gateway больше не нужны.

## 2. Настройка сервиса

1. Источник — этот GitHub-репозиторий, Root Directory пустой.
2. Dockerfile — `Dockerfile` в корне (Railway подхватит сам; иначе `RAILWAY_DOCKERFILE_PATH=Dockerfile`).
3. Start command не задавать — он в Dockerfile.
4. Healthcheck path: `/api/actuator/health`.
5. Порт: ничего не указывать, приложение слушает `PORT`, который задаёт Railway.
6. Публичный домен — у этого сервиса; всё API под `/api`.
7. Том для фото товаров, смонтированный в `/data/media`.

## 3. Переменные окружения

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...

ADMIN_EMAIL=<почта первого админа>
ADMIN_PASSWORD=<пароль первого админа>   # сменить после первого входа
GEM_CORS_ORIGINS=https://<домен фронта>,https://<домен админки>

CATALOG_MEDIA_DIR=/data/media
CATALOG_MEDIA_PUBLIC_URL=https://<домен>/api/catalog/media

MLM_SSO_SHARED_SECRET=<общий секрет с MLM-системой>
MLM_BACKEND_BASE_URL=<адрес MLM-бэка>
MLM_SYNC_SHARED_SECRET=<секрет обратной синхронизации>

FINANCE_MOCK_SIMULATOR_ENABLED=false
```

По необходимости:
- ИИ для фото и текстов — `PHOTO_AI_PROVIDER=gemini`, `PROOFREAD_PROVIDER=gemini`, `GEMINI_API_KEY`;
- Finik — `FINANCE_ACQUIRING_PROVIDER=finik`, `FINANCE_PAYOUT_PROVIDER=finik`, ключи `FINIK_*`,
  `FINIK_WEBHOOK_URL=https://<домен>/api/webhooks/acquiring/finik`.

Переменные старой схемы (`SPRING_KAFKA_*`, `GEM_JWT_JWK_SET_URI`, `GEM_INTERNAL_TOKEN`,
`AUTH_INTERNAL_TOKEN`, `GATEWAY_*`, `*_BASE_URL` соседних сервисов) больше не читаются — их можно удалить.

## 4. Проверка после деплоя

```
curl -i https://<домен>/api/actuator/health
curl -i -X POST https://<домен>/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"<ADMIN_EMAIL>","password":"<ADMIN_PASSWORD>"}'
```
Ожидаем `200` и пару токенов. `502` — приложение не поднялось, смотреть Deploy Logs;
`401` — неверные данные.

## 5. Локальная проверка образа

```
docker build -t gem .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/gem \
  -e SPRING_DATASOURCE_USERNAME=gem -e SPRING_DATASOURCE_PASSWORD=gem gem
```
