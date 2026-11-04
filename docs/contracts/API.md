# HTTP-контракты v1

Base: `/api`. JSON UTF-8. Cookie session; GET `/api/session` возвращает csrfToken для заголовка `X-CSRF-TOKEN` каждого изменяющего запроса. Время ISO-8601 UTC, даты YYYY-MM-DD, отображение в timezone марафона. ID UUID. Ошибки: `{error: string}` с 400/401/403/404/409/429. Никаких правильных ответов до финализации.

| Метод | Путь | Контракт |
|---|---|---|
| GET | /session | user nullable, csrfToken, serverTime |
| POST | /register | fullName,phone,password,terms,personalData,participation,smsConsent,rankingConsent |
| POST | /login | phone,password; устанавливает сессию |
| POST | /logout | завершает сессию |
| POST | /otp/request | phone,purpose VERIFY/RESET; одинаковый ответ для существующего/неизвестного телефона |
| POST | /otp/verify | phone,purpose,code,newPassword? |
| GET | /dashboard | доступные дети, марафоны, назначения |
| POST | /children | lastName,firstName,middleName,birthYear,category |
| POST | /consents | smsConsent,rankingConsent |
| POST | /join | childId,code |
| POST | /marathons | title,description,startDate,days,timezone,published |
| PATCH | /marathons/{id} | title,description,published,status |
| POST | /marathons/{id}/teachers | userId |
| GET | /marathons/{id} | участники, задания, результаты; scoped access |
| GET | /libraries | библиотеки текущего педагога, admin — все |
| POST | /libraries | multipart file (CSV), name |
| POST | /marathons/{id}/generate | childIds[],day,libraryIds[],count,level?,topic?,instruction |
| POST | /marathons/{id}/assignments | childId,day,instruction,examples:[{expression,answer}] |
| POST | /assignments/{id}/copy | childId,day |
| GET | /assignments/{id} | assignment,attempt,examples; эталоны только после завершения |
| POST | /assignments/{id}/start | идемпотентный старт, serverTime, deadline, version |
| PUT | /assignments/{id}/answers | version,answers:{exampleId:string}; optimistic concurrency |
| POST | /assignments/{id}/submit | version,answers; идемпотентно после завершения |
| POST | /assignments/{id}/reopen | reason,confirmed:true |
| GET | /marathons/{id}/ranking | rows с days,score,rank,tie,missedDays,completedDays |
| POST | /marathons/{id}/tie | childIds[] в порядке мест,reason; полная равная группа |
| GET | /marathons/{id}/export | CSV общего отчёта |
| GET | /children/{id}/export?marathonId= | CSV подробных ответов |
| GET | /admin/users | администратор: пользователи |
| PATCH | /admin/users/{id} | role,enabled |
| GET | /admin/audit | последние 500 событий |
| GET | /admin/sms | последние 200 доставок без кодов OTP |

## CSV-библиотека
Заголовок: `category,topic,level,expression,answer`. Категория BABY/SCHOOL, level BASIC/MEDIUM/HARD. Выражение — отображаемый текст (не исполняется), answer — целое в диапазоне Java int. UTF-8 с необязательным BOM, запятая, RFC 4180 quoting. ≤ 2 МБ / 10 000 строк; expression ≤ 200 символов. Пустые файлы, неизвестные поля/уровни и некорректные строки отклоняются. Случайный выбор SecureRandom, без повторов expression+answer даже между библиотеками. Отбор по категории ребёнка обязателен.

## Целостность
Уникально одно назначение на ребёнка/день/марафон. Блокировка строки assignment сериализует start/save/submit/reopen. Версия предотвращает потерю обновлений между устройствами. Старт/дедлайн определяет сервер. Каждая попытка хранится отдельно; рейтинг использует только текущую. Импорт, назначения, результаты и outbox сохраняются в одной транзакции. Права проверяются на каждом запросе сервером.
