"use strict";
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const labels = {
  PARENT: "Родитель",
  TEACHER: "Педагог",
  ADMIN: "Администратор",
  BABY: "Дошкольник",
  SCHOOL: "Школьник",
  DRAFT: "Черновик",
  SCHEDULED: "Запланировано",
  ACTIVE: "Идёт сейчас",
  COMPLETED: "Завершён",
  CANCELLED: "Отменён",
  AVAILABLE: "Можно начинать",
  IN_PROGRESS: "В работе",
  CHECKED: "Проверено",
  EXPIRED: "Время истекло",
  MISSED: "Не выполнено",
  CLOSED: "Закрыто",
  REOPENED: "Открыто повторно",
  INTERRUPTED: "Прервано",
  CORRECT: "Верно",
  ERROR: "Ошибка",
  SKIPPED: "Пропуск",
  BASIC: "Базовый",
  MEDIUM: "Средний",
  HARD: "Сложный",
};
let user,
  csrf,
  dashboard,
  detail,
  task,
  routeVersion = 0,
  timer,
  debounce,
  queue = Promise.resolve(),
  saveBlocked = false,
  taskEpoch = 0;
let currentChild = "",
  currentMarathon = "",
  currentTab = "tasks",
  currentAdminTab = "users";
let serverAnchor = Date.now(),
  clientAnchor = performance.now();
const serverNow = () =>
  new Date(serverAnchor + performance.now() - clientAnchor);
const navIcon = (p) =>
  ({
    home: "home",
    children: "users",
    libraries: "book-open",
    profile: "user",
    admin: "settings",
  })[p];
const childAvatar = (c) =>
  `<img class="avatar" src="/avatar-${c.category === "BABY" ? "girl" : "boy"}.webp" alt="" width="48" height="48">`;
const fullName = (c) =>
  [c.last_name, c.first_name, c.middle_name].filter(Boolean).join(" ");
const quantity = (n, one, few, many) =>
  `${n} ${{ one, few, many, other: many }[new Intl.PluralRules("ru").select(n)]}`;
const date = (v, options = {}) =>
  new Date(v).toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "long",
    timeZone: "Europe/Moscow",
    ...options,
  });
const stamp = (v, zone = "Europe/Moscow") =>
  v ? new Date(v).toLocaleString("ru-RU", { timeZone: zone }) : "—";
const pill = (v, cls = "") =>
  `<span class="pill ${cls}">${esc(labels[v] || v)}</span>`;
const empty = (t) => `<div class="empty">${esc(t)}</div>`;
const option = (v, t, selected) =>
  `<option value="${esc(v)}" ${v === selected ? "selected" : ""}>${esc(t)}</option>`;
const childOptions = (cs) =>
  cs.map((c) => option(c.id, fullName(c), currentChild)).join("");
const field = (name, label, type = "text", value = "", extra = "") =>
  `<label>${label}<input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
const check = (name, label, checked = false, required = false) =>
  `<label class="check"><input type="checkbox" name="${name}" ${checked ? "checked" : ""} ${required ? "required" : ""}><span>${label}</span></label>`;
function toast(text) {
  $("#toast").textContent = text;
  $("#toast").classList.add("visible");
  clearTimeout(toast.t);
  toast.t = setTimeout(() => $("#toast").classList.remove("visible"), 6000);
}
async function api(path, method = "GET", body) {
  const options = { method, credentials: "same-origin", headers: {} };
  if (method !== "GET") options.headers["X-CSRF-TOKEN"] = csrf;
  if (body instanceof FormData) options.body = body;
  else if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }
  const res = await fetch("/api" + path, options);
  let data;
  try {
    data = await res.json();
  } catch {
    data = { error: "Сервер не смог обработать запрос" };
  }
  if (!res.ok) {
    const e = new Error(data.error || "Ошибка запроса");
    e.status = res.status;
    throw e;
  }
  return data;
}
async function session() {
  const s = await api("/session");
  serverAnchor = new Date(s.serverTime).getTime();
  clientAnchor = performance.now();
  user = s.user;
  csrf = s.csrfToken;
  return s;
}
function modal(html) {
  $("#dialog-content").innerHTML = html;
  $("#dialog").showModal();
}
function close() {
  if ($("#dialog").open) $("#dialog").close();
}
function nav(path) {
  if (location.hash === "#" + path) render();
  else location.hash = path;
}
function shell(html, active = "home") {
  window.scrollTo(0, 0);
  const staff = user.role !== "PARENT";
  $("#app").innerHTML =
    `<div class="layout"><aside class="sidebar"><a class="brand" href="#home">Считай<span class="brand-dot">.</span></a><p class="tagline">Марафон<br>ментальной арифметики</p><nav class="nav" aria-label="Основная навигация">${[["home", staff ? "Марафоны" : "Главная"], ...(staff ? [["libraries", "Библиотеки"]] : [["children", "Мои дети"]]), ["profile", "Мой профиль"], ...(user.role === "ADMIN" ? [["admin", "Управление"]] : [])].map(([p, t]) => `<a href="#${p}" ${active === p ? 'class="active" aria-current="page"' : ""}><img class="nav-icon" src="/icons/${navIcon(p)}.svg" alt="">${t}</a>`).join("")}</nav><div class="side-bottom"><p>Каждый день —<br>ещё одна маленькая победа.</p><button data-action="logout">Выйти</button></div></aside><main id="main" class="content"><header class="topbar"><div><strong>${esc(staff ? "Кабинет организатора" : "Учимся считать уверенно")}</strong><div class="muted">${date(serverNow(), { year: "numeric" })}</div></div><a class="account" href="#profile"><span>${esc(user.full_name)}</span><span class="avatar" aria-hidden="true">${esc(user.full_name.charAt(0))}</span></a></header>${html}<footer>Считай · Ментальная арифметика <span aria-hidden="true"> / </span> <a href="#legal">Документы и согласия</a></footer></main></div>`;
}
function authScreen(mode = "login") {
  window.scrollTo(0, 0);
  const register = mode === "register";
  $("#app").innerHTML =
    `<div class="auth-shell"><section class="auth-art"><a class="brand" href="#login">Считай<span class="brand-dot">.</span></a><div><div class="eyebrow">Марафон ментальной арифметики</div><h1>Маленькие шаги.<br>Большие победы.</h1><p>Шесть дней, чтобы стать увереннее.<br>Один пример за другим.</p><img src="/hero.webp" alt="Дорожка через зелёные холмы к финишному флагу"></div><p>Ребёнок занимается в кабинете родителя.</p></section><main class="auth-main" id="main"><div class="eyebrow">Рады вас видеть</div><h2>${register ? "Начнём знакомство" : "С возвращением"}</h2><p>${register ? "Создайте аккаунт родителя. Профили детей добавим следующим шагом." : "Войдите, чтобы продолжить свой марафон."}</p><form data-form="${register ? "register" : "login"}">${register ? field("fullName", "Ваше имя и фамилия", "text", "", 'required maxlength="150" autocomplete="name"') : ""}${field("phone", "Номер телефона", "tel", "", 'required autocomplete="tel" placeholder="+7 900 000-00-00"')}${field("password", "Пароль", "password", "", 'required minlength="10" autocomplete="' + (register ? "new-password" : "current-password") + '"')}${register ? check("terms", 'Принимаю <a href="#legal" target="_blank">условия использования</a>', false, true) + check("personalData", 'Даю отдельное <a href="#legal" target="_blank">согласие на обработку персональных данных</a>', false, true) + check("participation", "Как законный представитель согласен на участие ребёнка в марафоне", false, true) + check("smsConsent", "Хочу получать SMS о заданиях и результатах (в том числе в 00:00)") + check("rankingConsent", "Разрешаю показывать полные ФИО ребёнка участникам рейтинга") : ""}<div class="form-error" role="alert"></div><button type="submit">${register ? "Создать аккаунт" : "Войти в кабинет"}</button></form><div class="links"><a href="#${register ? "login" : "register"}">${register ? "Уже есть аккаунт" : "Зарегистрироваться"}</a><button class="text-button" data-action="reset">Забыли пароль?</button></div></main></div>`;
}
async function render() {
  if (task?.status === "IN_PROGRESS" && $(".answer")) await saveAnswers();
  clearInterval(timer);
  clearTimeout(debounce);
  const version = ++routeVersion;
  taskEpoch++;
  const path = location.hash.slice(1) || "home";
  try {
    if (path === "legal") {
      legal();
      return;
    }
    if (!user) {
      authScreen(path === "register" ? "register" : "login");
      return;
    }
    if (path === "children") {
      await children();
      return;
    }
    if (path === "profile") {
      profile();
      return;
    }
    if (path === "libraries") {
      await libraryPage();
      return;
    }
    if (path === "admin") {
      await adminPage();
      return;
    }
    if (path.startsWith("assignment/")) {
      await assignmentPage(path.split("/")[1]);
      return;
    }
    if (path.startsWith("marathon/")) {
      await marathonPage(path.split("/")[1]);
      return;
    }
    dashboard = await api("/dashboard");
    if (version !== routeVersion) return;
    if (user.role === "PARENT") await home();
    else staffHome();
  } catch (e) {
    if (e.status === 401) {
      user = null;
      authScreen();
    } else {
      shell(
        `<h1>Не удалось открыть страницу</h1><div class="notice error">${esc(e.message)}</div><button data-action="refresh">Попробовать ещё раз</button>`,
      );
    }
  }
}
async function home() {
  if (!dashboard.children.some((c) => c.id === currentChild))
    currentChild = dashboard.children[0]?.id || "";
  if (!dashboard.marathons.some((m) => m.id === currentMarathon))
    currentMarathon = dashboard.marathons[0]?.id || "";
  detail = currentMarathon ? await api("/marathons/" + currentMarathon) : null;
  const child = dashboard.children.find((c) => c.id === currentChild);
  const tasks =
    detail?.assignments.filter((a) => a.child_id === currentChild) || [];
  const now = serverNow().getTime();
  const today =
    tasks.find((a) => a.reopened_until && new Date(a.reopened_until) > now) ||
    tasks.find(
      (a) => new Date(a.opens_at) <= now && new Date(a.closes_at) > now,
    );
  const active =
    detail?.marathon.effective_status === "ACTIVE" || today?.reopened_until;
  const finished =
    today &&
    ["CHECKED", "EXPIRED", "INTERRUPTED"].includes(today.attempt_status) &&
    !today.reopened_until;
  let ranking = [];
  if (detail?.marathon.published)
    try {
      ranking = await api("/marathons/" + currentMarathon + "/ranking");
    } catch {}
  shell(
    `${!user.verified ? '<div class="notice">Подтвердите телефон, чтобы вступать в марафоны и выполнять задания. <button class="text-button" data-action="verify">Подтвердить</button></div>' : ""}<div class="filter-bar">${dashboard.children.length ? `<label>Ребёнок<select id="child-select">${childOptions(dashboard.children)}</select></label>` : ""}${dashboard.marathons.length ? `<label>Марафон<select id="marathon-select">${dashboard.marathons.map((m) => option(m.id, m.title, currentMarathon)).join("")}</select></label>` : ""}</div><section class="hero"><img class="hero-img" src="/hero.webp" alt=""><div class="eyebrow">${esc(detail?.marathon.title || "Всё начинается с первого шага")}</div><h1>Маленькие шаги.<br>Большие победы.</h1><p>${esc(child ? child.first_name + ", считаем в своём темпе и становимся увереннее каждый день." : "Добавьте ребёнка и присоединитесь к марафону. Новые открытия уже близко.")}</p><div class="hero-actions">${today && active ? `<button class="orange" data-action="open-assignment" data-id="${today.id}">${finished ? "Посмотреть результат" : today.attempt_status === "IN_PROGRESS" ? "Продолжить задание" : "Открыть задание дня"}</button>${pill("День " + today.day + " · до 30 минут")}` : dashboard.children.length ? '<button class="orange" data-action="join">Присоединиться к марафону</button>' : '<button class="orange" data-action="add-child">Добавить ребёнка</button>'}</div></section>${
      detail
        ? `<div class="journey">${Array.from(
            { length: detail.marathon.days },
            (_, i) => {
              const a = tasks.find((a) => a.day === i + 1);
              const d = new Date(detail.marathon.start_date + "T12:00:00");
              d.setDate(d.getDate() + i);
              return `<button class="day ${a?.id === today?.id ? "current" : ""}" data-action="${a ? "open-assignment" : "no-assignment"}" data-id="${a?.id || ""}"><span class="day-number">${i + 1}</span><span>${date(d, { month: "short" })}</span><small>${a?.attempt_status && a.attempt_status !== "IN_PROGRESS" ? quantity(a.correct, "балл", "балла", "баллов") : a?.id === today?.id ? "Сегодня" : "День " + (i + 1)}</small></button>`;
            },
          ).join("")}</div>`
        : ""
    }<div class="split"><section><div class="section-head"><h2>Мои дети</h2><button class="text-button" data-action="add-child">Добавить</button></div>${dashboard.children.length ? dashboard.children.map((c) => `<div class="row">${childAvatar(c)}<div class="row-main"><div class="row-title">${esc(c.first_name)}</div><div class="row-sub">${esc(fullName(c))} · ${labels[c.category]}</div></div><button class="outline small" data-action="select-child" data-id="${c.id}">Выбрать</button></div>`).join("") : empty("Здесь появятся профили ваших детей.")}</section><section><div class="section-head"><h2>Рейтинг марафона</h2>${detail ? `<button class="text-button" data-action="ranking" data-id="${currentMarathon}">Все результаты</button>` : ""}</div>${
      ranking.length
        ? ranking
            .slice(0, 3)
            .map(
              (r) =>
                `<div class="row"><span class="rank-num">${r.rank}</span><div class="row-main"><div class="row-title">${esc(r.name)}</div><div class="row-sub">${r.tie ? "Равный результат" : labels[r.category]}</div></div><strong>${r.score} б.</strong></div>`,
            )
            .join("")
        : empty("Рейтинг появится, когда организатор опубликует результаты.")
    }</section></div>${detail ? `<div class="section-head"><h2>Все дни и результаты</h2><a class="button outline small" href="#marathon/${currentMarathon}">Открыть марафон</a></div>${assignmentTable(tasks, detail.children)}` : ""}`,
  );
}
function staffHome() {
  shell(
    `<div class="section-head"><div><div class="eyebrow">Пространство педагога</div><h1>Каждый ребёнок<br>может больше.</h1></div><button data-action="new-marathon">Создать марафон</button></div><p class="muted">Марафоны, индивидуальные задания и маленькие победы ваших учеников.</p><div class="grid">${dashboard.marathons.map((m) => `<article class="panel"><div class="actions">${pill(m.effective_status)}<span class="helper">${quantity(m.days, "день", "дня", "дней")}</span></div><h2 class="section-head">${esc(m.title)}</h2><p class="muted">${esc(m.description)}</p><div class="row"><span class="helper">С ${date(m.start_date + "T12:00:00")} · ${esc(m.timezone)}</span><a class="button small" href="#marathon/${m.id}">Открыть</a></div></article>`).join("") || empty("Создайте первый марафон, загрузите библиотеку примеров и пригласите участников.")}</div>`,
  );
}
async function children() {
  dashboard = await api("/dashboard");
  shell(
    `<div class="section-head"><div><div class="eyebrow">Семейный кабинет</div><h1>Мои дети</h1></div><button data-action="add-child">Добавить ребёнка</button></div><p class="muted">Один аккаунт родителя — отдельная история успехов для каждого ребёнка.</p>${dashboard.children.map((c) => `<div class="row">${childAvatar(c)}<div class="row-main"><div class="row-title">${esc(fullName(c))}</div><div class="row-sub">${c.birth_year} год рождения · ${labels[c.category]}</div></div><button class="outline" data-action="select-child" data-id="${c.id}">Задания</button></div>`).join("") || empty("Добавьте первый профиль, чтобы начать.")}<div class="section-head"><button class="secondary" data-action="join">Присоединиться по коду марафона</button></div>`,
    "children",
  );
}
function profile() {
  shell(
    `<div class="eyebrow">Настройки аккаунта</div><h1>Мой профиль</h1><div class="grid"><section class="panel"><h2>${esc(user.full_name)}</h2><p>+${esc(user.phone)} · ${labels[user.role]}</p>${pill(user.verified ? "Телефон подтверждён" : "Телефон не подтверждён", user.verified ? "" : "bad")}<div class="section-head">${!user.verified ? '<button data-action="verify">Подтвердить телефон</button>' : ""}<button class="outline" data-action="reset">Сменить пароль</button></div></section><section class="panel"><h2>Ваши согласия</h2>${user.role === "PARENT" ? `<form class="stack" data-form="consents">${check("smsConsent", "SMS о заданиях и результатах, включая уведомления в 00:00", user.sms_consent)}${check("rankingConsent", "Публикация полных ФИО ребёнка в рейтинге для участников марафона", user.ranking_consent)}<p class="helper">Согласия можно отозвать в любой момент. Отзыв SMS не отключает запрошенные вами коды подтверждения и восстановления.</p><div class="form-error" role="alert"></div><button>Сохранить согласия</button></form>` : '<p class="muted">Управление персональными данными — по обращению к оператору платформы.</p>'}</section></div>`,
    "profile",
  );
}
function assignmentTable(as, cs) {
  return as.length
    ? `<div class="table-wrap"><table><thead><tr><th>Ребёнок</th><th>День</th><th>Статус</th><th>Баллы</th><th>Ошибки</th><th>Пропуски</th><th></th></tr></thead><tbody>${as
        .map((a) => {
          let st =
            a.attempt_status ||
            (new Date(a.closes_at) < serverNow()
              ? "MISSED"
              : new Date(a.opens_at) > serverNow()
                ? "SCHEDULED"
                : "AVAILABLE");
          if (a.reopened_until && new Date(a.reopened_until) > serverNow())
            st = "REOPENED";
          return `<tr><td>${esc(fullName(cs.find((c) => c.id === a.child_id) || {}))}</td><td>${a.day}</td><td>${pill(st)}</td><td>${a.correct ?? "—"}</td><td>${a.errors ?? "—"}</td><td>${a.skipped ?? "—"}</td><td><a class="button outline small" href="#assignment/${a.id}">Открыть</a></td></tr>`;
        })
        .join("")}</tbody></table></div>`
    : empty("Задания ещё не назначены.");
}
async function marathonPage(mid) {
  detail = await api("/marathons/" + mid);
  currentMarathon = mid;
  const m = detail.marathon,
    staff = user.role !== "PARENT";
  let content = "";
  if (currentTab === "ranking") {
    const rows = await api("/marathons/" + mid + "/ranking");
    content = rankingTable(rows, m);
    if (staff)
      content += `<div class="section-head"><a class="button outline" href="/api/marathons/${mid}/export">Скачать CSV-отчёт</a>${m.effective_status === "COMPLETED" ? '<button data-action="tie">Зафиксировать очную встречу</button>' : ""}</div>`;
  } else if (currentTab === "people") {
    content =
      detail.children
        .map(
          (c) =>
            `<div class="row">${childAvatar(c)}<div class="row-main"><div class="row-title">${esc(fullName(c))}</div><div class="row-sub">${c.birth_year} · ${labels[c.category]}</div></div>${staff ? `<a class="button outline small" href="/api/children/${c.id}/export?marathonId=${mid}">Отчёт CSV</a>` : ""}</div>`,
        )
        .join("") ||
      empty("Участники появятся после вступления родителей по коду.");
  } else if (currentTab === "settings" && staff) {
    content = `<form class="panel stack" data-form="settings"><h2>Настройки марафона</h2>${field("title", "Название", "text", m.title, 'required maxlength="150"')}<label>Описание<textarea name="description" maxlength="2000">${esc(m.description)}</textarea></label><label>Статус<select name="status">${["DRAFT", "SCHEDULED", "CANCELLED"].map((s) => option(s, s === "SCHEDULED" ? "Опубликован · по расписанию" : labels[s], m.status)).join("")}</select></label>${check("published", "Публиковать рейтинг для участников", m.published)}<p class="helper">Дата начала: ${date(m.start_date + "T12:00:00")}. Часовой пояс: ${esc(m.timezone)}. Расписание фиксируется при создании.</p><div class="form-error" role="alert"></div><button>Сохранить</button></form><section class="panel history"><h2>Педагоги</h2>${detail.teachers.map((t) => `<p>${esc(t.full_name)}</p>`).join("")}${user.role === "ADMIN" ? '<button data-action="add-teacher">Назначить педагога</button>' : ""}</section>`;
  } else {
    content = `${staff ? '<div class="actions section-head"><button data-action="generate">Сгенерировать задания</button><button class="outline" data-action="manual">Добавить вручную</button></div>' : ""}${assignmentTable(detail.assignments, detail.children)}`;
  }
  shell(
    `<a class="helper" href="#home">Все марафоны</a><div class="section-head"><h1>${esc(m.title)}</h1>${pill(m.effective_status)}</div><p class="muted">${esc(m.description)}</p><div class="actions"><span class="helper">${date(m.start_date + "T12:00:00")} · ${quantity(m.days, "день", "дня", "дней")} · ${esc(m.timezone)}</span>${staff ? `<span class="pill">Код: ${esc(m.join_code)}</span><button class="outline small" data-action="copy-code">Копировать код</button>` : ""}</div><div class="tabs" role="navigation" aria-label="Разделы марафона">${[["tasks", "Задания"], ["people", "Участники"], ...(staff || m.published ? [["ranking", "Рейтинг"]] : []), ...(staff ? [["settings", "Настройки"]] : [])].map(([t, l]) => `<button class="${currentTab === t ? "active" : ""}" data-action="tab" data-tab="${t}">${l}</button>`).join("")}</div>${content}`,
  );
}
function rankingTable(rows, m) {
  return rows.length
    ? `<p class="helper">При равных баллах участники получают одинаковое место. Итоговый порядок определяет очная встреча.</p><div class="table-wrap"><table><thead><tr><th>Место</th><th>Участник</th><th>Категория</th>${Array.from({ length: m.days }, (_, i) => `<th>Д${i + 1}</th>`).join("")}<th>Итого</th><th>Выполнено</th><th>Пропущено дней</th><th>Решение</th></tr></thead><tbody>${rows.map((r) => `<tr><td class="rank-num">${r.rank}</td><td><strong>${esc(r.name)}</strong></td><td>${labels[r.category]}</td>${r.days.map((d) => `<td>${d}</td>`).join("")}<td><strong>${r.score}</strong></td><td>${r.completed_days}</td><td>${r.missed_days}</td><td>${r.tie ? pill("Требуется очная встреча", "bad") : esc(r.decision || "—")}</td></tr>`).join("")}</tbody></table></div>`
    : empty("В марафоне пока нет участников.");
}
async function libraryPage() {
  const libs = await api("/libraries");
  shell(
    `<div class="section-head"><div><div class="eyebrow">Материалы для занятий</div><h1>Библиотеки примеров</h1></div><button data-action="upload">Загрузить CSV</button></div><p class="muted">Один файл — одна библиотека. Объединяйте несколько библиотек при генерации заданий.</p><div class="notice">Формат: category, topic, level, expression, answer. Категории BABY / SCHOOL, уровни BASIC / MEDIUM / HARD. <a href="/sample.csv" download>Скачать пример CSV</a></div>${libs.map((l) => `<div class="row"><div class="row-main"><div class="row-title">${esc(l.name)}</div><div class="row-sub">${stamp(l.created_at)}</div></div><span class="pill">${l.example_count} примеров</span></div>`).join("") || empty("Загрузите первый файл с примерами. Импорт проверит каждую строку.")}`,
    "libraries",
  );
}
async function adminPage() {
  if (user.role !== "ADMIN") throw new Error("Нет доступа");
  const rows = await api("/admin/" + currentAdminTab);
  let html;
  if (currentAdminTab === "users")
    html = `<div class="table-wrap"><table><thead><tr><th>Пользователь</th><th>Телефон</th><th>Роль</th><th>Статус</th><th></th></tr></thead><tbody>${rows.map((u) => `<tr><td>${esc(u.full_name)}</td><td>+${esc(u.phone)}</td><td>${labels[u.role]}</td><td>${u.enabled ? "Активен" : "Отключён"}</td><td>${u.id !== user.id ? `<button class="outline small" data-action="edit-user" data-id="${u.id}" data-role="${u.role}" data-enabled="${u.enabled}">Изменить</button>` : ""}</td></tr>`).join("")}</tbody></table></div>`;
  else
    html = `<div class="table-wrap"><table><thead><tr>${(currentAdminTab === "audit" ? ["Время", "Кто", "Действие", "Описание"] : ["Время", "Событие", "Статус", "Попыток", "Ошибка"]).map((x) => `<th>${x}</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr>${(currentAdminTab === "audit" ? [stamp(r.at), r.actor_name || "Система", r.action, r.detail] : [stamp(r.created_at), r.event_key, r.status, r.attempts, r.last_error || "—"]).map((x) => `<td>${esc(x)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
  shell(
    `<div class="eyebrow">Администрирование</div><h1>Всё под контролем</h1><div class="tabs">${[
      ["users", "Пользователи"],
      ["audit", "Журнал действий"],
      ["sms", "SMS-очередь"],
    ]
      .map(
        ([v, t]) =>
          `<button data-action="admin-tab" data-tab="${v}" class="${v === currentAdminTab ? "active" : ""}">${t}</button>`,
      )
      .join("")}</div>${html}`,
    "admin",
  );
}
async function assignmentPage(aid) {
  task = await api("/assignments/" + aid);
  saveBlocked = false;
  queue = Promise.resolve();
  renderTask();
}
function renderTask() {
  const t = task,
    a = t.assignment,
    attempt = t.attempt,
    parent = user.role === "PARENT",
    active = t.status === "IN_PROGRESS",
    done =
      attempt && ["CHECKED", "EXPIRED", "INTERRUPTED"].includes(attempt.status);
  let content = "";
  if (active && parent) {
    content = `<div class="task-header"><div><strong id="answered">0 из ${t.examples.length}</strong><p class="helper" id="save-state" aria-live="polite">Ответы сохраняются автоматически</p></div><div><div class="timer" id="timer" aria-label="Оставшееся время">30:00</div><span class="helper">до завершения</span></div></div><progress class="progress" id="progress" max="${t.examples.length}" value="0" aria-label="Заполнено ответов"></progress><div id="time-warning" role="status" aria-live="polite"></div><div class="exercises">${t.examples.map((e, i) => `<div class="exercise"><span class="number">${String(i + 1).padStart(2, "0")}</span><span class="expression" id="expr-${e.id}">${esc(e.expression)} =</span><label><span class="visually-hidden">Ответ на пример ${i + 1}: ${esc(e.expression)}</span><input class="answer" data-id="${e.id}" inputmode="text" autocomplete="off" pattern="-?[0-9]*" maxlength="11" value="${esc(e.value ?? "")}" aria-describedby="expr-${e.id}"></label></div>`).join("")}</div><div class="task-footer"><span class="helper">После отправки изменить ответы нельзя.</span><button data-action="submit-task">Завершить и отправить</button></div>`;
  } else if (parent && ["AVAILABLE", "REOPENED"].includes(t.status)) {
    content = `<section class="panel"><h2>${t.status === "REOPENED" ? "Попробуем ещё раз" : "Готовы начать?"}</h2><p>${t.exampleCount} примеров. На работу — 30 минут с момента нажатия кнопки${t.status === "AVAILABLE" ? ", но не позднее закрытия дня" : ""}.</p><p class="muted">Таймер продолжит идти при закрытии страницы. Ответы сохраняются во время работы.</p><button class="orange" data-action="start-task">Начать задание</button></section>`;
  } else if (done) {
    content = `<div class="notice">Работа выполнена. Каждый правильный ответ — один балл. Ошибки и пропуски считаются отдельно.</div><div class="stat-grid">${[
      ["Баллы", attempt.correct],
      ["Правильно", attempt.correct],
      ["Ошибки", attempt.errors],
      ["Пропуски", attempt.skipped],
    ]
      .map(
        ([l, v]) =>
          `<div class="stat"><strong>${v}</strong><span>${l}</span></div>`,
      )
      .join(
        "",
      )}</div><h2>Разбор задания</h2>${answerTable(t.examples)}<p class="helper history">Начало: ${stamp(attempt.started_at, t.marathon.timezone)} · Завершение: ${stamp(attempt.finished_at, t.marathon.timezone)}</p>`;
  } else if (!parent && t.examples.length) {
    content = `${active ? '<div class="notice">Ребёнок выполняет задание. Результат появится после завершения.</div>' : ""}${answerTable(t.examples)}`;
  } else
    content = empty(
      t.status === "SCHEDULED"
        ? "Задание откроется " + stamp(t.opensAt, t.marathon.timezone)
        : t.status === "MISSED"
          ? "Этот день пропущен. Можно продолжить в следующий день."
          : t.status === "DRAFT"
            ? "Педагог ещё не опубликовал марафон."
            : "Задание закрыто.",
    );
  shell(
    `<a class="helper" href="#marathon/${t.marathon.id}">${esc(t.marathon.title)}</a><div class="section-head"><div><div class="eyebrow">В своём темпе, с вниманием к каждому примеру</div><h1>${esc(t.child?.first_name || "")} · день ${a.day}. У тебя получится.</h1></div>${pill(t.status)}</div><p class="muted">${esc(a.instruction || "Впиши ответ в поле рядом с примером.")}</p>${content}${!parent ? '<div class="actions history"><button class="outline" data-action="copy-task">Копировать задание</button><button class="outline" data-action="reopen">Повторно открыть после сбоя</button></div>' : ""}`,
  );
  if (active && parent) {
    updateProgress();
    const base = performance.now(),
      remaining = new Date(attempt.deadline) - new Date(t.serverTime);
    let warned5 = false,
      warned1 = false;
    const epoch = taskEpoch;
    timer = setInterval(() => {
      if (epoch !== taskEpoch) return;
      const ms = Math.max(0, remaining - (performance.now() - base));
      const secs = Math.ceil(ms / 1000);
      $("#timer").textContent =
        String(Math.floor(secs / 60)).padStart(2, "0") +
        ":" +
        String(secs % 60).padStart(2, "0");
      $("#timer").classList.toggle("urgent", secs <= 300);
      if (secs <= 300 && !warned5) {
        $("#time-warning").innerHTML =
          '<div class="notice">Осталось менее 5 минут. Проверь заполненные ответы.</div>';
        warned5 = true;
      }
      if (secs <= 60 && !warned1) {
        $("#time-warning").innerHTML =
          '<div class="notice">Осталась 1 минута. Ответы будут отправлены автоматически.</div>';
        warned1 = true;
      }
      if (secs === 0) {
        clearInterval(timer);
        clearTimeout(debounce);
        $$(".answer").forEach((i) => (i.disabled = true));
        queue = queue.then(async () => {
          try {
            task = await api("/assignments/" + a.id);
            if (task.status === "IN_PROGRESS")
              task = await api("/assignments/" + a.id + "/submit", "POST", {
                version: task.attempt.version,
                answers: {},
              });
            renderTask();
          } catch (e) {
            toast(
              "Время закончилось. Сервер завершит работу по сохранённым ответам. Обновите страницу для результата.",
            );
          }
        });
      }
    }, 250);
  }
}
function answerTable(es) {
  return `<div class="table-wrap"><table><thead><tr><th>№</th><th>Пример</th><th>Ответ ребёнка</th><th>Правильный ответ</th><th>Результат</th></tr></thead><tbody>${es.map((e, i) => `<tr><td>${i + 1}</td><td>${esc(e.expression)}</td><td>${esc(e.value ?? "—")}</td><td>${esc(e.answer ?? "—")}</td><td class="${e.result === "CORRECT" ? "ok" : e.result === "ERROR" ? "wrong" : ""}">${esc(labels[e.result] || "Ещё не проверено")}</td></tr>`).join("")}</tbody></table></div>`;
}
function answers() {
  return Object.fromEntries(
    $$(".answer").map((i) => [i.dataset.id, i.value.trim()]),
  );
}
function updateProgress() {
  const count = $$(".answer").filter((i) => i.value.trim()).length;
  if ($("#answered"))
    $("#answered").textContent =
      count + " из " + task.examples.length + " заполнено";
  if ($("#progress")) $("#progress").value = count;
}
function saveAnswers() {
  if (saveBlocked || !task || task.status !== "IN_PROGRESS") return queue;
  const values = answers(),
    aid = task.assignment.id,
    epoch = taskEpoch;
  queue = queue.then(async () => {
    if (saveBlocked || epoch !== taskEpoch) return;
    try {
      if ($("#save-state")) $("#save-state").textContent = "Сохраняем…";
      const result = await api("/assignments/" + aid + "/answers", "PUT", {
        version: task.attempt.version,
        answers: values,
      });
      if (epoch !== taskEpoch) return;
      task = result;
      if (task.status !== "IN_PROGRESS") {
        clearInterval(timer);
        renderTask();
        return;
      }
      if ($("#save-state"))
        $("#save-state").textContent =
          "Сохранено на сервере · " + new Date().toLocaleTimeString("ru-RU");
    } catch (e) {
      if (e.status === 409) {
        saveBlocked = true;
        toast(e.message);
        if ($("#save-state"))
          $("#save-state").textContent =
            "Конфликт устройств. Обновите страницу перед продолжением.";
      } else if ($("#save-state"))
        $("#save-state").textContent =
          "Не сохранено. Проверьте связь; следующая попытка через 5 секунд.";
      if (!saveBlocked && epoch === taskEpoch)
        debounce = setTimeout(saveAnswers, 5000);
    }
  });
  return queue;
}
function formBody(form) {
  const data = Object.fromEntries(new FormData(form));
  $$("input[type=checkbox]", form).forEach((i) => {
    if (!i.name.endsWith("[]")) data[i.name] = i.checked;
  });
  return data;
}
function addChild() {
  modal(
    `<h2>Новый маленький герой</h2><form class="form-grid" data-form="child">${field("lastName", "Фамилия", "text", "", 'required maxlength="80"')}${field("firstName", "Имя", "text", "", 'required maxlength="80"')}${field("middleName", "Отчество · необязательно", "text", "", 'maxlength="80"')}${field("birthYear", "Год рождения", "number", new Date().getFullYear() - 7, `required min="${new Date().getFullYear() - 25}" max="${new Date().getFullYear()}"`)}<label class="wide">Категория<select name="category">${option("BABY", "Дошкольник")}${option("SCHOOL", "Школьник")}</select></label><div class="form-error wide" role="alert"></div><button class="wide">Добавить ребёнка</button></form>`,
  );
}
async function joinDialog() {
  dashboard = await api("/dashboard");
  if (!dashboard.children.length) {
    addChild();
    return;
  }
  modal(
    `<h2>Присоединиться к марафону</h2><form class="stack" data-form="join"><label>Ребёнок<select name="childId">${childOptions(dashboard.children)}</select></label>${field("code", "Код от педагога", "text", "", 'required maxlength="16" autocomplete="off"')}<div class="form-error" role="alert"></div><button>Присоединиться</button></form>`,
  );
}
function otpDialog(purpose) {
  modal(
    `<h2>${purpose === "VERIFY" ? "Подтвердить телефон" : "Восстановить доступ"}</h2><p class="muted">Мы отправим запрошенный вами сервисный код. Он действует 10 минут.</p><form class="stack" data-form="otp-request"><input type="hidden" name="purpose" value="${purpose}">${field("phone", "Номер телефона", "tel", user?.phone || "", 'required autocomplete="tel"')}<div class="form-error" role="alert"></div><button>Получить код</button></form>`,
  );
}
function marathonDialog() {
  modal(
    `<h2>Новый марафон</h2><form class="form-grid" data-form="marathon">${field("title", "Название", "text", "", 'required maxlength="150"')}<label>Дата старта<input type="date" name="startDate" value="${new Date().toLocaleDateString("en-CA")}" required></label>${field("days", "Количество дней", "number", "6", 'required min="1" max="31"')}${field("timezone", "Часовой пояс", "text", "Europe/Moscow", 'required maxlength="80"')}<label class="wide">Описание<textarea name="description" maxlength="2000"></textarea></label><div class="wide">${check("published", "Публиковать рейтинг для участников", true)}</div><p class="helper wide">Марафон создаётся как черновик. После подготовки заданий опубликуйте его в настройках.</p><div class="form-error wide" role="alert"></div><button class="wide">Создать марафон</button></form>`,
  );
}
async function generateDialog() {
  const libs = await api("/libraries");
  if (!detail.children.length) {
    toast("Сначала пригласите участников по коду марафона.");
    return;
  }
  if (!libs.length) {
    toast("Сначала загрузите CSV-библиотеку.");
    nav("libraries");
    return;
  }
  modal(
    `<h2>Индивидуальные задания</h2><p class="helper">Каждому ребёнку — случайная выборка его категории. Без повторов внутри задания.</p><form class="stack" data-form="generate"><fieldset><legend>Участники</legend><div class="select-list">${detail.children.map((c) => `<label class="check"><input type="checkbox" name="childIds[]" value="${c.id}" checked><span>${esc(fullName(c))} · ${labels[c.category]}</span></label>`).join("")}</div></fieldset><fieldset><legend>Библиотеки</legend><div class="select-list">${libs.map((l) => `<label class="check"><input type="checkbox" name="libraryIds[]" value="${l.id}"><span>${esc(l.name)} · ${l.example_count} примеров</span></label>`).join("")}</div></fieldset><div class="form-grid">${field("day", "День марафона", "number", "1", `required min="1" max="${detail.marathon.days}"`)}${field("count", "Примеров на ребёнка", "number", "20", 'required min="1" max="200"')}<label>Уровень<select name="level">${option("", "Все уровни")}${["BASIC", "MEDIUM", "HARD"].map((x) => option(x, labels[x])).join("")}</select></label>${field("topic", "Тема · необязательно", "text", "", 'maxlength="80"')}</div><label>Инструкция<textarea name="instruction" maxlength="2000">Внимательно прочитай пример и впиши ответ. У тебя всё получится!</textarea></label><p class="helper">Неначатые задания выбранного дня будут заменены. Начатые задания изменить нельзя.</p><div class="form-error" role="alert"></div><button>Создать задания</button></form>`,
  );
}
function manualDialog() {
  if (!detail.children.length) {
    toast("Сначала пригласите участников.");
    return;
  }
  modal(
    `<h2>Задание вручную</h2><form class="stack" data-form="manual"><label>Ребёнок<select name="childId">${childOptions(detail.children)}</select></label>${field("day", "День марафона", "number", "1", `required min="1" max="${detail.marathon.days}"`)}<label>Примеры: выражение = ответ<textarea name="lines" required rows="7" placeholder="25 + 14 - 7 = 32&#10;9 - 12 = -3"></textarea></label><p class="helper">По одному примеру на строку. Разделитель — последний знак =. Только целые ответы.</p><label>Инструкция<textarea name="instruction" maxlength="2000"></textarea></label><div class="form-error" role="alert"></div><button>Назначить</button></form>`,
  );
}
async function copyDialog() {
  detail = await api("/marathons/" + task.marathon.id);
  modal(
    `<h2>Копировать задание</h2><form class="stack" data-form="copy"><label>Ребёнок<select name="childId">${childOptions(detail.children)}</select></label>${field("day", "День", "number", task.assignment.day, `required min="1" max="${detail.marathon.days}"`)}<div class="form-error" role="alert"></div><button>Копировать</button></form>`,
  );
}
async function tieDialog() {
  const rows = await api("/marathons/" + currentMarathon + "/ranking");
  modal(
    `<h2>Решение очной встречи</h2><p class="helper">Укажите всю группу с одинаковыми баллами в порядке итоговых мест. Баллы и ответы останутся прежними.</p><form class="stack" data-form="tie"><div class="select-list">${rows.map((r) => `<label>${esc(r.name)} · ${r.score} б.<input type="number" name="rank-${r.child_id}" min="1" max="${rows.length}" placeholder="Порядок в группе · пусто = не выбирать"></label>`).join("")}</div><label>Дата, основание и решение<textarea name="reason" required minlength="10" maxlength="1000"></textarea></label><div class="form-error" role="alert"></div><button>Зафиксировать места</button></form>`,
  );
}
async function addTeacherDialog() {
  const users = await api("/admin/users");
  modal(
    `<h2>Назначить педагога</h2><form class="stack" data-form="teacher"><label>Педагог<select name="userId">${users
      .filter((u) => u.enabled && ["TEACHER", "ADMIN"].includes(u.role))
      .map((u) => option(u.id, u.full_name))
      .join(
        "",
      )}</select></label><div class="form-error" role="alert"></div><button>Назначить</button></form>`,
  );
}
async function legal() {
  let config = {
    operator: "Организатор марафона",
    contact: "Уточните у педагога",
  };
  try {
    config = await api("/public-config");
  } catch {}
  $("#app").innerHTML =
    `<main id="main" class="legal"><a class="brand" href="#home">Считай.</a><h1 class="history">Документы и согласия</h1><p>Версия от 5 ноября 2026 года. Оператор: ${esc(config.operator)}. Контакт для обращений: ${esc(config.contact)}.</p><h2>Условия использования</h2><p>Платформа проводит образовательные марафоны. Родитель действует как законный представитель ребёнка, указывает достоверные сведения и обеспечивает доступ ребёнка только через свой аккаунт. Организатор определяет расписание, задания и порядок очной встречи при равных баллах. Один верный ответ приносит один балл; ошибки и пропуски учитываются отдельно.</p><h2>Согласие на обработку данных</h2><p>Отдельной отметкой при регистрации родитель разрешает оператору обрабатывать свои имя, фамилию и телефон, а также ФИО, год рождения, категорию, ответы и результаты ребёнка для регистрации, проведения марафона, проверки работ и связи по вопросам участия. Операции: сбор, запись, хранение, уточнение, использование и удаление с применением автоматизации. Пароль хранится в виде защищённого хеша.</p><p>Согласие действует до отзыва. Для отзыва обязательного согласия, получения копии данных или удаления аккаунта обратитесь к оператору по указанному контакту. После отзыва участие прекращается; оператор удаляет данные, кроме подлежащих обязательному хранению. В резервных копиях удаление происходит при плановом истечении их срока хранения.</p><h2>Согласие на участие</h2><p>Отдельной отметкой законный представитель разрешает ребёнку участвовать в марафонах и подтверждает правила выполнения: ограничение времени, автоматическая проверка, невозможность редактировать отправленную работу, повторное открытие только после подтверждённого технического сбоя.</p><h2>SMS-уведомления — по желанию</h2><p>Отдельная необязательная отметка разрешает передавать телефон и текст уведомления сервису SMS.RU и отправлять сообщения о ходе марафона, включая открытие задания в 00:00. Согласие отзывается в профиле. Сервисные коды подтверждения и восстановления отправляются только по отдельному запросу пользователя.</p><h2>Публикация рейтинга — по желанию</h2><p>Отдельная необязательная отметка разрешает показывать полные ФИО, категорию и результаты ребёнка авторизованным участникам этого марафона после публикации рейтинга организатором. При отказе или отзыве ФИО заменяются подписью «Участник без публикации ФИО». Педагог и администратор видят данные для организации обучения.</p><h2>Хранение и безопасность</h2><p>Используются серверная проверка доступа, защищённые сессии, журнал действий и резервное копирование. Технические сессии необходимы для работы сайта. Данные не используются для рекламы. Вопросы обработки данных и удаления направляйте оператору.</p><a class="button" href="#home">Вернуться в кабинет</a></main>`;
}
document.addEventListener("click", async (ev) => {
  const el = ev.target.closest("[data-action]");
  if (!el) return;
  const action = el.dataset.action;
  try {
    switch (action) {
      case "close":
        close();
        break;
      case "logout":
        await api("/logout", "POST");
        user = null;
        await session();
        nav("login");
        break;
      case "refresh":
        render();
        break;
      case "add-child":
        addChild();
        break;
      case "join":
        await joinDialog();
        break;
      case "verify":
        otpDialog("VERIFY");
        break;
      case "reset":
        otpDialog("RESET");
        break;
      case "new-marathon":
        marathonDialog();
        break;
      case "open-assignment":
        nav("assignment/" + el.dataset.id);
        break;
      case "no-assignment":
        toast("Педагог ещё не назначил задание на этот день.");
        break;
      case "select-child":
        currentChild = el.dataset.id;
        nav("home");
        break;
      case "ranking":
        currentTab = "ranking";
        nav("marathon/" + el.dataset.id);
        break;
      case "tab":
        currentTab = el.dataset.tab;
        await marathonPage(currentMarathon);
        break;
      case "admin-tab":
        currentAdminTab = el.dataset.tab;
        await adminPage();
        break;
      case "generate":
        await generateDialog();
        break;
      case "manual":
        manualDialog();
        break;
      case "copy-task":
        await copyDialog();
        break;
      case "tie":
        await tieDialog();
        break;
      case "add-teacher":
        await addTeacherDialog();
        break;
      case "copy-code":
        await navigator.clipboard.writeText(detail.marathon.join_code);
        toast("Код скопирован");
        break;
      case "upload":
        modal(
          `<h2>Новая библиотека</h2><form class="stack" data-form="upload">${field("name", "Название библиотеки", "text", "", 'required maxlength="120"')}<label>CSV-файл · UTF-8<input name="file" type="file" accept=".csv,text/csv" required></label><p class="helper">До 2 МБ и 10 000 строк. <a href="/sample.csv" download>Скачать образец</a></p><div class="form-error" role="alert"></div><button>Проверить и загрузить</button></form>`,
        );
        break;
      case "edit-user":
        modal(
          `<h2>Управление пользователем</h2><form class="stack" data-form="user"><input type="hidden" name="id" value="${el.dataset.id}"><label>Роль<select name="role">${["PARENT", "TEACHER", "ADMIN"].map((r) => option(r, labels[r], el.dataset.role)).join("")}</select></label>${check("enabled", "Аккаунт активен", el.dataset.enabled === "true")}<p class="helper">Изменение роли завершит предыдущие сессии пользователя.</p><div class="form-error" role="alert"></div><button>Сохранить</button></form>`,
        );
        break;
      case "start-task":
        el.disabled = true;
        task = await api(
          "/assignments/" + task.assignment.id + "/start",
          "POST",
        );
        renderTask();
        break;
      case "reopen":
        modal(
          `<h2>Повторное открытие</h2><form class="stack" data-form="reopen"><label>Причина технического сбоя<textarea name="reason" minlength="10" maxlength="1000" required></textarea></label>${check("confirmed", "Технический сбой подтверждён. Я понимаю, что действие будет записано в журнал.", false, true)}<p class="helper">Предыдущая попытка сохранится. На новую попытку будет отведено 30 минут; начать нужно в течение 24 часов.</p><div class="form-error" role="alert"></div><button>Повторно открыть</button></form>`,
        );
        break;
      case "submit-task":
        modal(
          `<h2>Завершить работу?</h2><p>Заполнено ${$$(".answer").filter((i) => i.value.trim()).length} из ${task.examples.length} ответов. После отправки изменить ответы будет нельзя.</p><div class="actions"><button data-action="confirm-submit">Завершить и отправить</button><button class="outline" data-action="close">Продолжить решать</button></div>`,
        );
        break;
      case "confirm-submit":
        el.disabled = true;
        clearTimeout(debounce);
        await saveAnswers();
        if (saveBlocked)
          throw new Error(
            "Обновите страницу: ответы изменились на другом устройстве.",
          );
        if (task.status === "IN_PROGRESS")
          task = await api(
            "/assignments/" + task.assignment.id + "/submit",
            "POST",
            { version: task.attempt.version, answers: answers() },
          );
        clearInterval(timer);
        close();
        renderTask();
        toast("Работа проверена");
        break;
    }
  } catch (e) {
    toast(e.message);
    el.disabled = false;
  }
});
document.addEventListener("submit", async (ev) => {
  const f = ev.target;
  if (!f.dataset.form) return;
  ev.preventDefault();
  const button = $("button[type=submit],button:not([type])", f);
  if (button) button.disabled = true;
  const error = $(".form-error", f);
  if (error) error.textContent = "";
  const b = formBody(f);
  try {
    switch (f.dataset.form) {
      case "login":
        await api("/login", "POST", b);
        await session();
        nav("home");
        break;
      case "register":
        await api("/register", "POST", b);
        await api("/login", "POST", { phone: b.phone, password: b.password });
        await session();
        nav("home");
        toast("Аккаунт создан. Подтвердите телефон в профиле.");
        break;
      case "child":
        await api("/children", "POST", b);
        close();
        await render();
        toast("Профиль ребёнка добавлен");
        break;
      case "join":
        await api("/join", "POST", b);
        close();
        nav("home");
        toast("Вы присоединились к марафону");
        break;
      case "consents":
        await api("/consents", "POST", b);
        await session();
        profile();
        toast("Согласия сохранены");
        break;
      case "otp-request":
        await api("/otp/request", "POST", b);
        $("#dialog-content").innerHTML =
          `<h2>Введите код из SMS</h2><p class="helper">Если номер зарегистрирован, код отправлен. В локальном режиме сообщение находится в тестовой очереди.</p><form class="stack" data-form="otp-verify"><input type="hidden" name="phone" value="${esc(b.phone)}"><input type="hidden" name="purpose" value="${b.purpose}">${field("code", "Код из 6 цифр", "text", "", 'required pattern="[0-9]{6}" maxlength="6" inputmode="numeric" autocomplete="one-time-code"')}${b.purpose === "RESET" ? field("newPassword", "Новый пароль", "password", "", 'required minlength="10" autocomplete="new-password"') : ""}<div class="form-error" role="alert"></div><button>Подтвердить</button></form>`;
        break;
      case "otp-verify":
        await api("/otp/verify", "POST", b);
        close();
        await session();
        await render();
        toast(
          b.purpose === "VERIFY"
            ? "Телефон подтверждён"
            : "Пароль изменён. Войдите снова.",
        );
        break;
      case "marathon": {
        const m = await api("/marathons", "POST", b);
        close();
        currentTab = "settings";
        nav("marathon/" + m.id);
        break;
      }
      case "settings":
        await api("/marathons/" + currentMarathon, "PATCH", b);
        await marathonPage(currentMarathon);
        toast("Настройки сохранены");
        break;
      case "generate":
        b.childIds = new FormData(f).getAll("childIds[]");
        b.libraryIds = new FormData(f).getAll("libraryIds[]");
        await api("/marathons/" + currentMarathon + "/generate", "POST", b);
        close();
        currentTab = "tasks";
        await marathonPage(currentMarathon);
        toast("Индивидуальные задания созданы");
        break;
      case "manual":
        b.examples = b.lines
          .split("\n")
          .filter((l) => l.trim())
          .map((l) => {
            const at = l.lastIndexOf("=");
            if (at < 1)
              throw new Error(
                "Каждая строка должна содержать выражение = ответ",
              );
            return {
              expression: l.slice(0, at).trim(),
              answer: l.slice(at + 1).trim(),
            };
          });
        await api("/marathons/" + currentMarathon + "/assignments", "POST", b);
        close();
        currentTab = "tasks";
        await marathonPage(currentMarathon);
        break;
      case "upload":
        await api("/libraries", "POST", new FormData(f));
        close();
        await libraryPage();
        toast("Библиотека проверена и загружена");
        break;
      case "copy":
        await api("/assignments/" + task.assignment.id + "/copy", "POST", b);
        close();
        toast("Задание скопировано");
        break;
      case "reopen":
        await api("/assignments/" + task.assignment.id + "/reopen", "POST", b);
        close();
        await assignmentPage(task.assignment.id);
        toast("Задание открыто повторно");
        break;
      case "tie": {
        const selected = Object.entries(b)
          .filter(([k, v]) => k.startsWith("rank-") && v)
          .map(([k, v]) => ({ id: k.slice(5), rank: Number(v) }));
        if (new Set(selected.map((r) => r.rank)).size !== selected.length)
          throw new Error("Порядковые номера не должны повторяться");
        b.childIds = selected.sort((a, b) => a.rank - b.rank).map((r) => r.id);
        await api("/marathons/" + currentMarathon + "/tie", "POST", b);
        close();
        await marathonPage(currentMarathon);
        break;
      }
      case "teacher":
        await api("/marathons/" + currentMarathon + "/teachers", "POST", b);
        close();
        await marathonPage(currentMarathon);
        break;
      case "user":
        await api("/admin/users/" + b.id, "PATCH", b);
        close();
        await adminPage();
        break;
    }
  } catch (e) {
    if (error && error.isConnected) error.textContent = e.message;
    else toast(e.message);
  } finally {
    if (button && button.isConnected) button.disabled = false;
  }
});
document.addEventListener("input", (e) => {
  if (e.target.matches(".answer")) {
    updateProgress();
    clearTimeout(debounce);
    debounce = setTimeout(saveAnswers, 650);
  }
});
document.addEventListener("change", (e) => {
  if (e.target.id === "child-select") {
    currentChild = e.target.value;
    render();
  }
  if (e.target.id === "marathon-select") {
    currentMarathon = e.target.value;
    render();
  }
});
window.addEventListener("hashchange", () => {
  close();
  render();
});
window.addEventListener("online", () => {
  if (task?.status === "IN_PROGRESS") saveAnswers();
});
window.addEventListener("beforeunload", (e) => {
  if (task?.status === "IN_PROGRESS" && $(".answer")) {
    saveAnswers();
    e.preventDefault();
    e.returnValue = "";
  }
});
session()
  .then(render)
  .catch(() => {
    $("#app").innerHTML =
      '<main class="loading"><h1>Нет связи с сервером</h1><p>Проверьте подключение и обновите страницу.</p></main>';
  });
