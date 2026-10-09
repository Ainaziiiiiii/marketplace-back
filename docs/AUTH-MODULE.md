# Модуль auth

Учётные записи, роли (RBAC), JWT + refresh с ротацией, OTP по email/телефону, SSO-вход из
внешней MLM-системы. Схема БД `auth`, пути — под `/api` общего приложения. Контракт — `docs/TASK-01-auth-service.md`,
`docs/ARCHITECTURE.md` §2.1, §3.1, §5.

## Запуск

Как всё приложение — см. README. `GET http://localhost:8080/api/actuator/health` → `UP`.

## Эндпоинты

| Метод | Путь | Описание |
|---|---|---|
| POST | `/auth/register` | регистрация внешнего клиента (email/телефон + пароль), шлёт OTP |
| POST | `/auth/otp/request` | запросить/перевыпустить OTP (`{ target, purpose }`) |
| POST | `/auth/otp/verify` | подтвердить OTP (`purpose = registration \| login`) → токены |
| POST | `/auth/login` | вход по паролю → токены |
| POST | `/auth/token/refresh` | ротация refresh-токена |
| POST | `/auth/logout` | отзыв refresh-токена |
| GET | `/auth/me` | профиль текущего пользователя (Bearer JWT) |
| POST | `/auth/sso/mlm` | вход по SSO-токену из внешней MLM-системы |
| GET | `/oauth2/jwks` | публичный ключ для проверки JWT (для внешних потребителей токена) |

Ошибки — `ProblemDetail` (RFC 7807), поле `code` — стабильный машиночитаемый код
(см. `AuthErrors`, статусы — `AuthExceptionHandler`).

## Env-переменные

| Переменная | Назначение | Дефолт (dev) |
|---|---|---|
| `SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD` | общая БД приложения | `localhost:5432/gem`, `gem/gem` |
| `AUTH_JWT_ISSUER` / `AUTH_JWT_AUDIENCE` | claims access-JWT | `green-eco-mall` / `green-eco-mall` |
| `MLM_SSO_SHARED_SECRET` | HS256-секрет для проверки SSO-токена от `mock-mlm` | `local-dev-mlm-shared-secret-change-me` |
| `MLM_SSO_ISSUER` / `MLM_SSO_AUDIENCE` | ожидаемые `iss`/`aud` SSO-токена | `mock-mlm` / `green-eco-mall` |

Ключ подписи JWT (RSA 2048) сейчас генерируется эфемерно при старте — после рестарта старые
access-токены становятся невалидными (TODO в `JwtConfig` на прод-хранение ключа).

## Ручной прогон

Коллекция запросов — `docs/http/auth.http`.

1. `POST /auth/register` → код OTP смотри в логе приложения (`LoggingOtpSender`, заглушка вместо
   реальной отправки).
2. `POST /auth/otp/verify` с `purpose=registration` → `accessToken`/`refreshToken`.
3. `GET /auth/me` с `Authorization: Bearer <accessToken>`.
4. SSO: `GET http://localhost:9100/sso/issue-token?mlm_user_id=U1` (mock-mlm) → токен →
   `POST /auth/sso/mlm` → наш JWT, `created=true` при первом входе.
