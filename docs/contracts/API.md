# HTTP-контракты v1

Base: `/api`. JSON UTF-8. Cookie session; GET `/api/session` возвращает csrfToken для заголовка `X-CSRF-TOKEN` каждого изменяющего запроса. Время ISO-8601 UTC, даты YYYY-MM-DD, отображение в timezone марафона. ID UUID. Ошибки: `{error: string}` с 400/401/403/404/409/429. Никаких правильных ответов до финализации.

| Метод | Путь | Контракт |
|---|---|---|
| GET | /session | user nullable, csrfToken, serverTime, competitionCategories (code, label, preschoolOnly) |
| POST | /register | fullName,email,password (от 6 символов),terms,personalData,participation,emailConsent,rankingConsent |
| POST | /login | email,password; устанавливает сессию |
| POST | /logout | завершает сессию |
| POST | /otp/request | email,purpose RESET. Одинаковый ответ для существующего и неизвестного адреса |
| POST | /otp/verify | email,purpose RESET,code,newPassword |
| GET | /dashboard | доступные дети, марафоны, назначения |
| POST | /children | lastName, firstName, middleName, birthDate (ГГГГ-ММ-ДД), category, gender (MALE/FEMALE). Для старых клиентов принимается birthYear |
| PATCH | /children/{id}/gender | gender (MALE/FEMALE). Доступно родителю ребёнка |
| PATCH | /children/{id}/birth-date | birthDate (ГГГГ-ММ-ДД). Доступно родителю ребёнка |
| DELETE | /children/{id} | Удаляет карточку из кабинета её владельца. История марафонов сохраняется |
| POST | /consents | smsConsent?, rankingConsent?. Обновляются переданные поля |
| POST | /join | childId,code |
| POST | /marathons | title,description,startDate,days,timezone,published,preschoolExampleCount,schoolExampleCount,taskDurationMinutes |
| PATCH | /marathons/{id} | title?, description?, published?, status?, startDate?, preschoolExampleCount?, schoolExampleCount?, taskDurationMinutes?. Изменение даты сдвигает расписание, сроки начатых попыток сохраняются |
| DELETE | /marathons/{id} | Только admin. Необратимо удаляет марафон, задания, попытки, ответы, результаты, силлабус, зачисления и назначения педагогов; аккаунты и карточки детей сохраняются |
| POST | /marathons/{id}/syllabus | multipart file (.xlsx). Заменяет силлабус и дневные количества только этого марафона |
| POST | /marathons/{id}/teachers | userIds[] (или устаревший userId). Добавляет нескольких педагогов; каждый получает доступ ко всем участникам и заданиям марафона |
| PATCH | /marathons/{id}/participants/{childId}/competition-category | competitionCategory из списка /session. Пустая строка снимает назначение в этом марафоне |
| GET | /marathons/{id} | Участники, задания и результаты с учётом прав доступа. Для педагога и администратора `assignableDays` содержит открытые и будущие дни для назначения, а `children[].age` — полные годы на текущую дату в часовом поясе марафона или null для карточки с одним годом рождения |
| GET | /libraries | библиотеки текущего педагога, admin — все |
| POST | /libraries | multipart file (CSV), name, competitionCategory? |
| DELETE | /libraries/{id} | Удаляет файл и его примеры из библиотеки. Доступно владельцу или администратору. Созданные задания сохраняют свои примеры |
| POST | /marathons/{id}/generate | childIds[],day,libraryIds[],count,level?,topic?,instruction. Для каждой категории выбранных участников требуется ровно один файл той же категории; лишние и несовпадающие файлы отклоняются |
| PUT | /assignments/{id} | instruction, examples[{expression,answer}]. Педагог может изменить задание только до начала попытки ребёнка |
| POST | /marathons/{id}/assignments | childId,day,instruction,examples:[{expression,answer}] |
| POST | /assignments/{id}/copy | childId,day |
| GET | /assignments/{id} | assignment,attempt,examples; эталоны только после завершения |
| POST | /assignments/{id}/start | идемпотентный старт, serverTime, deadline, version |
| PUT | /assignments/{id}/answers | version,answers:{exampleId:string}; optimistic concurrency |
| POST | /assignments/{id}/submit | version,answers; идемпотентно после завершения |
| POST | /assignments/{id}/reopen | reason,confirmed:true |
| GET | /marathons/{id}/ranking | Места считаются отдельно для SCHOOL и BABY. Родителю доступны группы его участвующих детей |
| POST | /marathons/{id}/tie | childIds[] в порядке мест, reason. Все участники с одинаковыми баллами в одной группе SCHOOL или BABY |
| GET | /marathons/{id}/export | Общий CSV с местом внутри SCHOOL или BABY. Категория заданий приводится справочно |
| GET | /children/{id}/export?marathonId= | CSV подробных ответов |
| GET | /admin/users | администратор: пользователи |
| GET | /admin/marathons | администратор: все марафоны для просмотра и удаления |
| PATCH | /admin/users/{id} | role,enabled |
| GET | /admin/audit | последние 500 событий |
| GET | /admin/mail | последние 200 доставок без текста писем и кодов OTP |

## CSV-библиотека
Заголовок: `category,topic,level,expression,answer`. Категория BABY/SCHOOL, level BASIC/MEDIUM/HARD. Выражение — отображаемый текст (не исполняется), answer — целое в диапазоне Java int. UTF-8 с необязательным BOM, запятая, RFC 4180 quoting. ≤ 2 МБ / 10 000 строк; expression ≤ 200 символов. Пустые файлы, неизвестные поля/уровни и некорректные строки отклоняются. Случайный выбор SecureRandom, без повторов expression+answer даже между библиотеками. Отбор по категории ребёнка обязателен.

Категория соревнования назначается всему файлу при загрузке. Генерация сверяет её с назначением участника, а колонку `category` — с его группой BABY или SCHOOL. Для каждой категории участников нужно передать ровно один файл: смешивать несколько файлов одной категории или передавать файл другой категории запрещено. Для «Законов на +10» допустимы только строки BABY. Участнику без назначенной категории задания не генерируются.

## Целостность
Уникально одно назначение на ребёнка/день/марафон. Блокировка строки assignment сериализует start/save/submit/reopen. Версия предотвращает потерю обновлений между устройствами. Старт/дедлайн определяет сервер. Каждая попытка хранится отдельно; рейтинг использует только текущую. Импорт, назначения, результаты и outbox сохраняются в одной транзакции. Права проверяются на каждом запросе сервером.
