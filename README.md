# Banking API

Реализация тестового задания Java: пользователи, контакты, JWT, начисление процентов и переводы.

## Стек

Java 11, Spring Boot 2.7.18, Maven 3.9.9 (Wrapper), PostgreSQL 15, Spring JDBC, Flyway 9.22.3, Spring Security OAuth2 Resource Server / JWT HS256, springdoc-openapi 1.8.0, JUnit 5, Mockito, Testcontainers.

Версии Spring Boot и springdoc выбраны для совместимости с требуемой Java 11: [Spring Boot 2.7](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements), [springdoc](https://github.com/springdoc/springdoc-openapi). Архитектура: `api → service → dao`. SQL выполняется через JdbcTemplate, все параметры запросов передаются отдельно от SQL. Frontend не требуется по заданию.

## Быстрый запуск с Docker

Нужны Docker Desktop / Docker Engine и Compose. Команды выполняются из корня проекта.

1. Скопировать `.env.example` в `.env`.
2. Указать `POSTGRES_PASSWORD` и сгенерировать `JWT_SECRET` (случайные 32 байта в Base64).
3. Запустить `docker compose up --build -d`.

Генерация секрета на Linux/macOS:

```bash
openssl rand -base64 32
```

Генерация в PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

API: `http://localhost:8080`. Swagger UI: [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). OpenAPI: [localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

Compose включает профиль `demo`, который создаёт пользователей отдельной Flyway-миграцией:

| ID | Имя | Email | Phone | Начальный депозит |
|---|---|---|---|---|
| 1 | Анна Иванова | user1@example.com | 79207865431 | 1000.00 |
| 2 | Борис Петров | user2@example.com | 79207865432 | 1000.00 |
| 3 | Вера Соколова | user3@example.com | 79207865433 | 1000.00 |

Демонстрационный пароль всех трёх: `Password123!`. Это публичные тестовые данные. Начисления начинают менять баланс после запуска.

`docker compose down` останавливает приложение и сохраняет данные в томе. Профиль без `demo` применяет только схему; пользователей следует создавать миграцией в одной транзакции вместе со счётом и контактами. Операция регистрации через API отсутствует.

## Запуск без контейнера приложения

Установить JDK 11; Maven устанавливать отдельно не нужно. PostgreSQL должен быть запущен, БД — создана.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/banking
export DB_USERNAME=banking
export DB_PASSWORD=your-db-password
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Windows PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banking'
$env:DB_USERNAME = 'banking'
$env:DB_PASSWORD = 'your-db-password'
$env:JWT_SECRET = '<Base64 key from the generation example>'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

Для готового JAR: `./mvnw package`, затем `java -jar target/banking-api-1.0.0.jar --spring.profiles.active=demo` с теми же переменными окружения. Приложение не читает `.env` самостоятельно; его читает Docker Compose.

## API

Все операции, кроме получения токена и документации, требуют `Authorization: Bearer <token>`. Любой авторизованный пользователь может искать и читать любого пользователя; баланс доступен только владельцу через `/me`. Пароль/хеш не возвращается.

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/auth/token` | Вход по `login` (email или phone) и `password` |
| GET | `/api/users` | Поиск и пагинация |
| GET | `/api/users/{id}` | Профиль, email и phone пользователя |
| GET | `/api/users/me` | Собственный профиль и баланс |
| POST | `/api/users/me/emails` | Добавить email: `{"email":"new@example.com"}` |
| PUT | `/api/users/me/emails/{id}` | Изменить свой email |
| DELETE | `/api/users/me/emails/{id}` | Удалить свой email, если останется другой |
| POST | `/api/users/me/phones` | Добавить phone: `{"phone":"79207865434"}` |
| PUT | `/api/users/me/phones/{id}` | Изменить свой phone |
| DELETE | `/api/users/me/phones/{id}` | Удалить свой phone, если останется другой |
| POST | `/api/transfers` | Перевести деньги, обязателен `Idempotency-Key: <UUID>` |

Контакты возвращаются как `{"id":1,"value":"user1@example.com"}`; `{id}` в адресах контактов — ID контакта, не пользователя.

### Примеры curl

```bash
curl -X POST http://localhost:8080/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"login":"user1@example.com","password":"Password123!"}'
```

Ответ содержит `accessToken`, `tokenType`, `expiresIn`. Далее заменить `TOKEN` полученным токеном.

```bash
curl 'http://localhost:8080/api/users?page=0&size=20&dateOfBirth=01.05.1993' \
  -H 'Authorization: Bearer TOKEN'

curl -X POST http://localhost:8080/api/transfers \
  -H 'Authorization: Bearer TOKEN' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 28f7b3bf-a4d0-4546-ac15-a8f8c16bb682' \
  -d '{"toUserId":2,"value":12.34}'
```

При сетевом сбое повторить перевод с **тем же ключом и телом**. Для нового перевода сгенерировать новый UUID. Повтор успешно выполненного запроса возвращает первоначальный перевод без повторного списания; повтор ключа с другими параметрами возвращает `409 IDEMPOTENCY_CONFLICT`. Отправитель всегда берётся из подписанного JWT `USER_ID`. Передача `fromUserId` в JSON отвергается.

### Поиск

Фильтры объединяются через AND:

* `dateOfBirth=dd.MM.yyyy` — дата рождения строго больше указанной;
* `phone` — точное совпадение, от 11 до 13 цифр, без `+` и пробелов;
* `email` — точное совпадение после приведения к нижнему регистру;
* `name` — регистрозависимый префикс; `%`, `_`, `!` в запросе считаются обычными символами;
* `page` — с 0; `size` — от 1 до 100, по умолчанию 20. Сортировка по ID.

Поля ответа: `content`, `page`, `size`, `totalElements`, `totalPages`. Даты в профилях имеют формат `dd.MM.yyyy`. Денежные значения хранятся как `BigDecimal` / `NUMERIC(19,2)`.

### Ошибки

`400` — невалидный JSON, неизвестное поле, неверный параметр, сумма или перевод самому себе. `401` — неверные данные входа, отсутствующий/просроченный/подделанный JWT. `404` — пользователь/счёт/собственный контакт не найден. `409` — занятый контакт, последний контакт, недостаток средств, превышение вместимости счёта или конфликт ключа. `503 RETRY_LATER` — таймаут блокировки при конкуренции; повторить запрос с тем же ключом.

Пример: `{"code":"INSUFFICIENT_FUNDS","message":"Insufficient funds","requestId":"..."}`. Заголовок `X-Request-Id` позволяет связать ответ с логом. Авторизационные ответы Security содержат `code` и `message`.

## Транзакции и ограничения

* Перевод — одна транзакция: два счёта блокируются `FOR UPDATE` по возрастанию `USER_ID`; затем проверяются повтор ключа, остаток и вместимость получателя. Списание, зачисление и запись перевода коммитятся вместе. Порядок блокировок одинаков для встречных переводов и начислений.
* Сумма строго положительна, не более двух знаков после запятой. Максимальное значение счёта/перевода — `99999999999999999.99`. Нельзя перевести самому себе или несуществующему пользователю. DB CHECK дополнительно запрещает отрицательный баланс.
* Уникальность email/phone обеспечивается БД. Email нормализуется в нижний регистр. Изменения контактов блокируют владельца, поэтому параллельные удаления не могут удалить последние два контакта. Ограничения целостности отложены до коммита: полную запись пользователя можно создать одной миграцией/транзакцией.
* Единственный счёт обеспечивается UNIQUE по `USER_ID`, обязательность счёта и минимум одного контакта каждого вида — отложенными constraint triggers. Начальный депозит неизменяем, хранится отдельно от текущего баланса.
* Пароли хешируются PBKDF2-HMAC-SHA256 с солью, 310000 итераций. Поддерживается полный диапазон 8–500 символов. JWT содержит `USER_ID`, а также технические `iss`, `iat`, `exp`; срок жизни — 3600 секунд. Секрет обязателен, общего встроенного ключа нет.
* Логи содержат ID запроса, метод, статус, время выполнения, ID и сумму подтверждённых переводов. Пароли, токены, контактные данные и query string не логируются.
* Прикладной кэш намеренно не добавлен: для небольшого задания он не обязателен, а балансы и изменяемые контакты должны читаться актуальными. Ответы защищённых API получают стандартные заголовки Spring Security `Cache-Control: no-cache, no-store`.

### Начисления

Раз в 30 секунд: `newBalance = min(round(balance × 1.10, 2), trunc(initialBalance × 2.07, 2), maxBalance)`. Округление процентов — HALF_UP; дробный предел 207% округляется вниз до копейки, чтобы не превысить его.

Для депозита 100: 110 → 121 → … → 207. Нулевой депозит поддерживается. Переводы могут увеличить баланс выше 207%; начисление **не уменьшает** такой баланс и пропускает его до снижения ниже предела. Исходный депозит после переводов не меняется. Если перевод снизил баланс, дальнейшее начисление снова работает относительно текущего баланса и исходного предела, как сформулировано в задании.

В нескольких экземплярах приложения строка `interest_state` сериализует начисления и проверяет интервал на часах PostgreSQL. Изменение времени последнего начисления и балансов происходит в одной транзакции. Пропущенные интервалы во время простоя не начисляются задним числом. При аварии транзакция откатывается целиком, следующий запуск повторит попытку. Отключение: `APP_INTEREST_ENABLED=false`.

## Проверка

Java 11 и работающий Docker необходимы для стандартного полного прогона. Тесты создают отдельный контейнер PostgreSQL, миграции применяются автоматически. Docker недоступен — тесты падают явно, не пропускаются.

```bash
./mvnw test          # unit-тесты переводов
./mvnw verify        # unit + интеграционные тесты MockMvc / Testcontainers
```

В Windows использовать `.\mvnw.cmd`. `.github/workflows/ci.yml` запускает `verify` на Java 11 в GitHub Actions.

Интеграционные тесты проверяют вход email/phone, JWT, фильтры, пагинацию, контакты и владение, валидацию, Swagger, банковские транзакции, конкурентное списание, встречные переводы, повтор запросов, откат при ошибке записи, начисления и DB-ограничения. Пользователи для тестов создаются через `TestUserDao`.

При неисправном Docker можно **явно** указать отдельную тестовую PostgreSQL:

```bash
./mvnw verify \
  -Dit.db.url=jdbc:postgresql://localhost:5432/banking_test \
  -Dit.db.user=postgres \
  -Dit.db.password=test
```

**База должна быть отдельной, пустой и предназначенной только для тестов:** перед каждым тестом очищаются пользовательские таблицы. Внешний режим выполняет те же тесты на реальном PostgreSQL; Testcontainers не запускается. Контейнерный режим остаётся режимом по умолчанию.

Проверка при реализации: JDK 11, 14 unit и 20 интеграционных тестов, 0 ошибок, 0 пропусков. Локально выполнен `mvn verify` на PostgreSQL 15.13 во внешнем режиме. Docker Desktop в исходной среде не запускался из-за ошибки сокета `dockerInference`; полный контейнерный режим Testcontainers успешно проверен в [GitHub Actions](https://github.com/stacktraceo/banking-api/actions).

## Структура

```text
src/main/java/ru/bank/api       REST, DTO, обработка ошибок
src/main/java/ru/bank/service   бизнес-правила и транзакции
src/main/java/ru/bank/dao       JDBC и блокировки PostgreSQL
src/main/java/ru/bank/config    безопасность, Swagger, логи
src/main/java/ru/bank/domain    модели данных
src/main/resources/db/migration/V1__schema.sql
src/main/java/db/demo/V2__DemoUsers.java
src/test/java/ru/bank/service/TransferServiceTest.java
src/test/java/ru/bank/BankingApiIT.java
```
