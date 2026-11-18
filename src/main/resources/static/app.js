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
const rankingCategories = { SCHOOL: "Школьники", BABY: "Дошкольники" };
const operationLabels = {
  ADDITION: "Сложение",
  SUBTRACTION: "Вычитание",
  MULTIPLICATION: "Умножение",
  DIVISION: "Деление",
};
const ageBandLabels = {
  "4_5": "4–5 лет",
  "6_7": "6–7 лет",
  "8_9": "8–9 лет",
  "10_11": "10–11 лет",
  "12_PLUS": "12+",
};
const syllabusSource = {
  "4_5": {
    NO_LAWS: "1,2,15;1,3,15;1,4,10;2,2,20",
    LAWS_5: "1,3,15;1,4,10;1,5,5;2,2,15;2,3,10;3,2,5",
    LAWS_PLUS_10: "1,2,15;1,3,15;1,4,10;2,2,15;2,3,5",
    LAWS_10: "1,2,15;1,3,15;1,4,10;2,2,15;2,3,5",
    ALL_LAWS: "1,3,10;1,4,10;1,5,10;2,2,10;2,3,10;3,2,10",
  },
  "6_7": {
    NO_LAWS: "1,2,15;1,3,15;1,4,10;2,2,10;2,3,10",
    LAWS_5: "1,3,10;1,4,10;1,5,10;2,2,10;2,3,10;3,2,10",
    LAWS_PLUS_10: "1,2,10;1,3,10;1,4,15;2,2,15;2,3,10",
    LAWS_10: "1,2,10;1,3,10;1,4,15;2,2,15;2,3,10",
    ALL_LAWS: "1,3,10;1,4,10;1,5,10;2,2,10;2,3,10;3,2,10",
  },
  "8_9": {
    NO_LAWS: "1,3,10;1,4,10;1,5,10;1,6,10;2,2,10;2,3,10;2,4,10;3,2,10;3,3,10;3,4,10",
    LAWS_5: "1,2,10;1,3,10;1,4,10;1,5,10;2,2,10;2,3,10;2,4,10;3,2,10;3,3,10;3,4,10",
    LAWS_10: "1,3,10;1,4,10;1,5,10;1,6,10;2,2,10;2,3,10;2,4,10;3,2,10;3,3,10;3,4,10",
    ALL_LAWS: "1,3,10;1,4,10;1,5,10;1,6,10;2,2,10;2,3,10;2,4,10;3,2,10;3,3,10;3,4,10",
    SINGLE_DIGIT: "2,2,25,MULTIPLICATION;3,2,20,MULTIPLICATION;4,2,15,MULTIPLICATION;3,2,20,DIVISION;4,2,20,DIVISION",
    MULTI_DIGIT: "2,2,25,MULTIPLICATION;3,2,20,MULTIPLICATION;4,2,15,MULTIPLICATION;4,2,20,DIVISION;5,2,20,DIVISION",
  },
  "10_11": {
    NO_LAWS: "1,4,5;1,5,15;1,6,10;1,7,10;2,2,5;2,3,15;2,4,10;3,2,5;3,3,15;3,4,10",
    LAWS_5: "1,4,5;1,5,15;1,6,10;1,7,10;2,2,5;2,3,15;2,4,10;3,2,5;3,3,15;3,4,10",
    LAWS_10: "1,3,5;1,4,5;1,5,15;1,6,15;2,2,5;2,3,15;2,4,10;3,2,5;3,3,15;3,4,10",
    ALL_LAWS: "1,3,5;1,4,5;1,5,15;1,6,15;2,2,5;2,3,15;2,4,10;3,2,5;3,3,15;3,4,10",
    SINGLE_DIGIT: "2,2,15,MULTIPLICATION;3,2,25,MULTIPLICATION;4,2,20,MULTIPLICATION;3,2,15,DIVISION;4,2,25,DIVISION",
    MULTI_DIGIT: "2,2,15,MULTIPLICATION;3,2,25,MULTIPLICATION;4,2,20,MULTIPLICATION;4,2,15,DIVISION;5,2,25,DIVISION",
  },
  "12_PLUS": {
    NO_LAWS: "1,3,5;1,4,10;1,5,15;1,6,10;2,2,5;2,3,10;2,4,15;3,2,5;3,3,10;3,4,15",
    LAWS_5: "1,3,5;1,4,10;1,5,15;1,6,10;2,2,5;2,3,10;2,4,15;3,2,5;3,3,10;3,4,15",
    LAWS_10: "1,3,5;1,4,5;1,5,15;1,6,15;2,2,5;2,3,10;2,4,15;3,2,5;3,3,10;3,4,15",
    ALL_LAWS: "1,3,5;1,4,5;1,5,15;1,6,15;2,2,5;2,3,10;2,4,15;3,2,5;3,3,10;3,4,15",
    SINGLE_DIGIT: "2,2,15,MULTIPLICATION;3,2,20,MULTIPLICATION;4,2,25,MULTIPLICATION;3,2,10,DIVISION;4,2,20,DIVISION;5,2,10,DIVISION",
    MULTI_DIGIT: "2,2,15,MULTIPLICATION;3,2,20,MULTIPLICATION;4,2,25,MULTIPLICATION;4,2,10,DIVISION;5,2,20,DIVISION;6,2,10,DIVISION",
  },
};
const ageBandFor = (age) => {
  if (age == null) return "";
  if (age <= 5) return "4_5";
  if (age <= 7) return "6_7";
  if (age <= 9) return "8_9";
  if (age <= 11) return "10_11";
  return "12_PLUS";
};
function defaultSyllabus(ageBand, competition) {
  const source = syllabusSource[ageBand]?.[competition];
  if (!source) return [];
  return source.split(";").flatMap((raw) => {
    const [digits, terms, count, operation] = raw.split(",");
    if (operation) return [{ digits: +digits, terms: +terms, count: +count, operation }];
    if (competition === "LAWS_PLUS_10")
      return [{ digits: +digits, terms: +terms, count: +count, operation: "ADDITION" }];
    const addition = Math.ceil(+count / 2);
    return [
      { digits: +digits, terms: +terms, count: addition, operation: "ADDITION" },
      { digits: +digits, terms: +terms, count: +count - addition, operation: "SUBTRACTION" },
    ].filter((row) => row.count);
  });
}
function marathonSyllabus(ageBand, category, competition) {
  const rows = (detail?.syllabus || [])
    .filter((row) => row.age_band === ageBand
      && row.category === category
      && row.competition_category === competition)
    .sort((a, b) => a.position - b.position)
    .map((row) => ({
      digits: row.digits,
      terms: row.terms,
      operation: row.operation,
      count: row.count,
    }));
  return rows.length ? rows : defaultSyllabus(ageBand, competition);
}
let competitionCategories = [];
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
function childAvatar(child) {
  const variant = { MALE: "boy", FEMALE: "girl" }[child.gender];
  if (variant) {
    const label = child.gender === "MALE" ? "Мальчик" : "Девочка";
    return `<img class="avatar" src="/avatar-${variant}.webp" alt="${label}" width="48" height="48">`;
  }
  return `<span class="avatar" aria-hidden="true">${esc(child.first_name?.charAt(0) ?? "")}</span>`;
}
const fullName = (c) =>
  [c.last_name, c.first_name, c.middle_name].filter(Boolean).join(" ");
const quantity = (n, one, few, many) =>
  `${n} ${{ one, few, many, other: many }[new Intl.PluralRules("ru").select(n)]}`;
const ageLabel = (age) =>
  age == null ? "Требуется дата рождения" : quantity(age, "год", "года", "лет");
const ageNote = "Возраст указан в полных годах на сегодня.";
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
const competitionLabel = (code) =>
  competitionCategories.find((item) => item.code === code)?.label || "Категория не назначена";
const competitionsFor = (category) =>
  competitionCategories.filter((item) => category !== "SCHOOL" || !item.preschoolOnly);
function verticalExpression(expression, id) {
  const text = String(expression).replace(/\s+/g, " ").trim();
  let rows;
  const product = text.match(/^(-?\d+)\s*([×÷])\s*(-?\d+)$/);
  if (product) {
    rows = [{ sign: "", number: product[1] }, { sign: product[2], number: product[3] }];
  } else {
    rows = [...text.matchAll(/([+-]?)\s*(\d+)/g)].map((match, index) => ({
      sign: index === 0 ? (match[1] === "-" ? "−" : "") : (match[1] === "-" ? "−" : "+"),
      number: match[2],
    }));
  }
  if (!rows.length) return `<span class="expression" id="${id}">${esc(text)} =</span>`;
  return `<span class="vertical-expression" id="${id}" role="img" aria-label="${esc(text)}">
    ${rows.map((row) => `<span class="vertical-number"><span>${row.sign}</span><strong>${esc(row.number)}</strong></span>`).join("")}
    <span class="vertical-rule" aria-hidden="true"></span>
  </span>`;
}
const competitionOptions = (selected, category) =>
  competitionsFor(category)
    .map((item) => option(item.code, item.label, selected))
    .join("");
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
  competitionCategories = s.competitionCategories;
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
function authArt() {
  return `<section class="auth-art"><a class="brand" href="#home">Считай<span class="brand-dot">.</span></a><div><div class="eyebrow">Марафон ментальной арифметики</div><h1>Маленькие шаги.<br>Большие победы.</h1><p>Шесть дней, чтобы стать увереннее.<br>Один пример за другим.</p><img src="/hero.webp" alt="Дорожка через зелёные холмы к финишному флагу"></div><p>Ребёнок занимается в кабинете родителя.</p></section>`;
}
function publicHome() {
  window.scrollTo(0, 0);
  $("#app").innerHTML = `<div class="auth-shell">${authArt()}<main class="auth-main" id="main"><div class="eyebrow">Считай вместе с нами</div><h2>Учимся считать уверенно</h2><p>Марафоны ментальной арифметики для детей. Занимайтесь в кабинете родителя и следите за прогрессом день за днём.</p><ul class="home-features"><li>Индивидуальные задания от педагога.</li><li>До 30 минут на выполнение задания дня.</li><li>Автосохранение ответов и подробный разбор результатов.</li></ul><div class="home-actions"><a class="button" href="#login">Войти в кабинет</a><a class="button outline" href="#register">Зарегистрироваться</a></div><div class="links"><a href="#legal">Документы и согласия</a></div></main></div>`;
}
function authScreen(mode = "login") {
  window.scrollTo(0, 0);
  const register = mode === "register";
  const email = field("email", "Электронная почта", "email", "", 'required maxlength="254" autocomplete="email" placeholder="name@example.ru"');
  const consents = register
    ? check("terms", 'Принимаю <a href="#legal" target="_blank">условия использования</a>', false, true)
      + check("personalData", 'Даю отдельное <a href="#legal" target="_blank">согласие на обработку персональных данных</a>', false, true)
      + check("participation", "Как законный представитель согласен на участие ребёнка в марафоне", false, true)
      + check("emailConsent", "Хочу получать письма о заданиях и результатах")
      + check("rankingConsent", "Разрешаю показывать полные ФИО ребёнка участникам рейтинга")
    : "";
  $("#app").innerHTML = `<div class="auth-shell">${authArt()}
    <main class="auth-main" id="main">
      <a class="button outline auth-home-link" href="#home"><span aria-hidden="true">←</span> На главную</a>
      <div class="eyebrow">Рады вас видеть</div>
      <h2>${register ? "Начнём знакомство" : "С возвращением"}</h2>
      <p>${register ? "Создайте аккаунт родителя. Профили детей добавим следующим шагом." : "Войдите, чтобы продолжить свой марафон."}</p>
      <form data-form="${register ? "register" : "login"}">
        ${register ? field("fullName", "Ваше имя и фамилия", "text", "", 'required maxlength="150" autocomplete="name"') : ""}
        ${email}
        ${field("password", "Пароль", "password", "", register
          ? 'required minlength="6" autocomplete="new-password" aria-describedby="registration-password-hint"'
          : 'required autocomplete="current-password"')}
        ${register ? '<span class="helper" id="registration-password-hint">Минимум 6 символов.</span>' : ""}
        ${consents}
        <div class="form-error" role="alert"></div>
        <button type="submit">${register ? "Создать аккаунт" : "Войти в кабинет"}</button>
      </form>
      <div class="links">
        <a href="#${register ? "login" : "register"}">${register ? "Уже есть аккаунт" : "Зарегистрироваться"}</a>
        <button class="text-button" data-action="reset">Забыли пароль?</button>
      </div>
    </main>
  </div>`;
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
      if (path === "home") publicHome();
      else authScreen(path === "register" ? "register" : "login");
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
    if (user.role === "PARENT") await home(version);
    else staffHome();
  } catch (e) {
    if (version !== routeVersion) return;
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
async function home(version) {
  if (!dashboard.children.some((c) => c.id === currentChild))
    currentChild = dashboard.children[0]?.id || "";
  if (!dashboard.marathons.some((m) => m.id === currentMarathon))
    currentMarathon = dashboard.marathons[0]?.id || "";
  const mid = currentMarathon;
  const nextDetail = mid ? await api("/marathons/" + mid) : null;
  if (version !== routeVersion) return;
  let ranking = null;
  if (nextDetail?.marathon.published) {
    try {
      ranking = await api("/marathons/" + mid + "/ranking");
    } catch {}
  }
  if (version !== routeVersion) return;
  detail = nextDetail;
  const child = dashboard.children.find((c) => c.id === currentChild);
  const tasks =
    detail?.assignments.filter((a) => a.child_id === currentChild) || [];
  const now = serverNow().getTime();
  const ongoing =
    tasks.find((a) => a.reopened_until && new Date(a.reopened_until) > now) ||
    tasks.find((a) => a.attempt_status === "IN_PROGRESS" && new Date(a.deadline) > now);
  const today = ongoing || tasks.find(
    (a) => new Date(a.opens_at) <= now && new Date(a.closes_at) > now,
  );
  const active =
    detail?.marathon.effective_status === "ACTIVE" ||
    (ongoing && detail.marathon.effective_status !== "CANCELLED");
  const finished =
    today &&
    ["CHECKED", "EXPIRED", "INTERRUPTED"].includes(today.attempt_status) &&
    !today.reopened_until;
  shell(
    `<div class="filter-bar">${dashboard.children.length ? `<label>Ребёнок<select id="child-select">${childOptions(dashboard.children)}</select></label>` : ""}${dashboard.marathons.length ? `<label>Марафон<select id="marathon-select">${dashboard.marathons.map((m) => option(m.id, m.title, currentMarathon)).join("")}</select></label>` : ""}</div><section class="hero"><img class="hero-img" src="/hero.webp" alt=""><div class="eyebrow">${esc(detail?.marathon.title || "Всё начинается с первого шага")}</div><h1>Маленькие шаги.<br>Большие победы.</h1><p>${esc(child ? child.first_name + ", считаем в своём темпе и становимся увереннее каждый день." : "Добавьте ребёнка и присоединитесь к марафону. Новые открытия уже близко.")}</p><div class="hero-actions">${today && active ? `<button class="orange" data-action="open-assignment" data-id="${today.id}">${finished ? "Посмотреть результат" : today.attempt_status === "IN_PROGRESS" ? "Продолжить задание" : "Открыть задание дня"}</button>${pill("День " + today.day + " · до " + (detail?.marathon.task_duration_minutes ?? 30) + " минут")}` : dashboard.children.length ? '<button class="orange" data-action="join">Присоединиться по коду марафона</button>' : '<button class="orange" data-action="add-child">Добавить ребёнка</button>'}</div></section>${
      detail
        ? `<div class="journey">${Array.from(
            { length: detail.marathon.days },
            (_, i) => {
              const a = tasks.find((a) => a.day === i + 1);
              const d = new Date(detail.marathon.start_date + "T12:00:00");
              d.setDate(d.getDate() + i);
              const current = a && a === today;
              const caption = a && a === ongoing
                ? (a.attempt_status === "IN_PROGRESS" ? labels.IN_PROGRESS : "Доступно")
                : a?.attempt_status && a.attempt_status !== "IN_PROGRESS"
                  ? quantity(a.correct, "балл", "балла", "баллов")
                  : current ? "Сегодня" : "День " + (i + 1);
              return `<button class="day ${current ? "current" : ""}" data-action="${a ? "open-assignment" : "no-assignment"}" data-id="${a?.id || ""}"><span class="day-number">${i + 1}</span><span>${date(d, { month: "short" })}</span><small>${caption}</small></button>`;
            },
          ).join("")}</div>`
        : ""
    }<div class="split"><section><div class="section-head"><h2>Мои дети</h2><button class="text-button" data-action="add-child">Добавить</button></div>${dashboard.children.length ? dashboard.children.map((c) => `<div class="row">${childAvatar(c)}<div class="row-main"><div class="row-title">${esc(c.first_name)}</div><div class="row-sub">${esc(fullName(c))} · ${labels[c.category]}</div></div><button class="outline small" data-action="select-child" data-id="${c.id}">Выбрать</button></div>`).join("") : empty("Здесь появятся профили ваших детей.")}</section><section><div class="section-head"><h2>Рейтинг марафона</h2>${detail?.marathon.published ? `<button class="text-button" data-action="ranking">Все результаты</button>` : ""}</div>${
      ranking
        ? rankingSections(ranking, (rows) => rows.slice(0, 3).map((r) =>
            `<div class="row"><span class="rank-num">${r.rank}</span><div class="row-main"><div class="row-title">${esc(r.name)}</div>${r.tie ? '<div class="row-sub">Равный результат</div>' : ""}</div><strong>${r.score} б.</strong></div>`,
          ).join(""), "h3")
        : empty(detail?.marathon.published
            ? "Не удалось загрузить рейтинг."
            : "Рейтинг появится, когда организатор опубликует результаты.")
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
  const cards = dashboard.children.map((child) => {
    const name = esc(fullName(child));
    const id = esc(child.id);
    const birth = child.birth_date
      ? date(child.birth_date, { year: "numeric" })
      : `${child.birth_year} год рождения`;
    const birthAction = child.birth_date ? "Изменить дату рождения" : "Указать дату рождения";
    return `<div class="row">
      ${childAvatar(child)}
      <div class="row-main">
        <div class="row-title">${name}</div>
        <div class="row-sub">${birth} · ${labels[child.category]}</div>
        <button class="text-button" data-action="child-birth-date" data-id="${id}"
          aria-label="${birthAction} для ${name}">${birthAction}</button>
        ${child.gender ? "" : `<button class="text-button" data-action="child-gender" data-id="${id}">Указать пол</button>`}
      </div>
      <div class="actions">
        <button class="outline" data-action="select-child" data-id="${id}">Задания</button>
        <button class="text-button child-remove" data-action="delete-child" data-id="${id}"
          aria-label="Удалить карточку ${name}">Удалить</button>
      </div>
    </div>`;
  }).join("");
  shell(
    `<div class="section-head">
      <div><div class="eyebrow">Семейный кабинет</div><h1>Мои дети</h1></div>
      <button data-action="add-child">Добавить ребёнка</button>
    </div>
    <p class="muted">Один аккаунт родителя — отдельная история успехов для каждого ребёнка.</p>
    ${cards || empty("Добавьте первый профиль, чтобы начать.")}
    <div class="section-head">
      <button class="secondary" data-action="join">Присоединиться по коду марафона</button>
    </div>`,
    "children",
  );
}
function profile() {
  shell(
    `<div class="eyebrow">Настройки аккаунта</div><h1>Мой профиль</h1><div class="grid"><section class="panel"><h2>${esc(user.full_name)}</h2><p>${esc(user.email)} · ${labels[user.role]}</p><div class="section-head"><button class="outline" data-action="reset">Сменить пароль</button></div></section><section class="panel"><h2>Ваши согласия</h2>${user.role === "PARENT" ? `<form class="stack" data-form="consents">${check("emailConsent", "Получать письма о заданиях и результатах", user.email_consent)}${check("rankingConsent", "Публикация полных ФИО ребёнка в рейтинге для участников марафона", user.ranking_consent)}<p class="helper">Согласия можно отозвать в любой момент.</p><div class="form-error" role="alert"></div><button>Сохранить согласия</button></form>` : '<p class="muted">Управление персональными данными — по обращению к оператору платформы.</p>'}</section></div>`,
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
  if (currentTab === "ranking" && !staff && !m.published) currentTab = "tasks";
  let content = "";
  if (currentTab === "ranking") {
    const rows = await api("/marathons/" + mid + "/ranking");
    content = rows.length
      ? '<p class="helper">При равных баллах участники одной категории получают одинаковое место. Итоговый порядок определяет очная встреча.</p>'
      : "";
    content += rankingSections(rows, (group, category) => {
      const table = rankingTable(group, m);
      if (!staff || m.effective_status !== "COMPLETED") return table;
      const scores = [...new Set(group.filter((r) => r.tie || r.decision).map((r) => r.score))];
      return scores.length
        ? table + `<div class="actions section-head">${scores.map((score) =>
            `<button class="outline" data-action="tie" data-category="${category}" data-score="${score}">Зафиксировать очную встречу · ${score} б.</button>`,
          ).join("")}</div>`
        : table;
    });
    if (staff)
      content += `<div class="section-head"><a class="button outline" href="/api/marathons/${mid}/export">Скачать CSV-отчёт</a></div>`;
  } else if (currentTab === "people") {
    const rows = detail.children.map((c) => {
      const summary = staff
        ? `${ageLabel(c.age)} · ${labels[c.category]} · ${c.birth_year} год рождения`
        : `${c.birth_year} · ${labels[c.category]}`;
      const competition = staff && !c.deleted_at
        ? `<form class="participant-competition" data-form="competition-category">
            <input type="hidden" name="childId" value="${c.id}">
            <label>Категория соревнования
              <select name="competitionCategory" aria-label="Категория соревнования для ${esc(fullName(c))}">
                ${option("", "Категория не назначена", c.competition_category)}
                ${competitionOptions(c.competition_category, c.category)}
              </select>
            </label>
            <button class="outline small">Сохранить</button>
            <div class="form-error" role="alert"></div>
          </form>`
        : (c.competition_category ? `<div class="row-sub">${esc(competitionLabel(c.competition_category))}</div>` : "");
      return `<div class="row">${childAvatar(c)}<div class="row-main"><div class="row-title">${esc(fullName(c))}</div><div class="row-sub">${summary}${c.deleted_at ? ' · Карточка удалена' : ''}</div>${competition}</div>${staff ? `<a class="button outline small" href="/api/children/${c.id}/export?marathonId=${mid}">Отчёт CSV</a>` : ""}</div>`;
    }).join("");
    content = rows
      ? (staff ? `<p class="helper">${ageNote}</p>` : "") + rows
      : empty("Участники появятся после вступления родителей по коду.");
  } else if (currentTab === "settings" && staff) {
    const countsLocked = m.syllabus_name ? "readonly" : "";
    content = `<form class="panel stack" data-form="settings"><h2>Настройки марафона</h2>${field("title", "Название", "text", m.title, 'required maxlength="150"')}<label>Описание<textarea name="description" maxlength="2000">${esc(m.description)}</textarea></label><label>Статус<select name="status">${["DRAFT", "SCHEDULED", "CANCELLED"].map((s) => option(s, s === "SCHEDULED" ? "Опубликован · по расписанию" : labels[s], m.status)).join("")}</select></label>${check("published", "Публиковать рейтинг для участников", m.published)}${field("startDate", "Дата начала", "date", m.start_date, "required")}${field("taskDurationMinutes", "Время на выполнение, минут", "number", m.task_duration_minutes, 'required min="1" max="240"')}${field("preschoolExampleCount", "Примеров дошкольникам", "number", m.preschool_example_count, `required min="1" max="200" ${countsLocked}`)}${field("schoolExampleCount", "Примеров школьникам", "number", m.school_example_count, `required min="1" max="200" ${countsLocked}`)}${m.syllabus_name ? '<p class="helper">Количество примеров определено прикреплённым силлабусом.</p>' : ""}<p class="helper">Часовой пояс: ${esc(m.timezone)}. При смене даты расписание сдвинется. Сроки уже начатых попыток сохранятся.</p><div class="form-error" role="alert"></div><button>Сохранить</button></form>
      <form class="panel stack history" data-form="syllabus"><h2>Силлабус марафона</h2><p>${m.syllabus_name ? `Сейчас прикреплён: <strong>${esc(m.syllabus_name)}</strong>` : "Используется встроенный силлабус."}</p><label>Новый силлабус Excel<input name="file" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" required></label><p class="helper">NF, F5, F10+, F10, FF и категории умножения/деления импортируются с сохранением порядка строк. Новый файл заменит правила только этого марафона.</p><div class="form-error" role="alert"></div><button>Прикрепить силлабус</button></form>
      <section class="panel history"><h2>Педагоги марафона</h2><p class="helper">Каждый назначенный педагог видит всех участников, задания, ответы и результаты этого марафона.</p>${detail.teachers.map((t) => `<p>${esc(t.full_name)}</p>`).join("")}${user.role === "ADMIN" ? '<button data-action="add-teacher">Добавить педагогов</button>' : ""}</section>`;
  } else {
    content = `${staff ? '<div class="actions section-head"><button data-action="generate">Сгенерировать задания</button><button class="outline" data-action="manual">Добавить вручную</button></div>' : ""}${assignmentTable(detail.assignments, detail.children)}`;
  }
  shell(
    `<a class="helper" href="#home">Все марафоны</a><div class="section-head"><h1>${esc(m.title)}</h1>${pill(m.effective_status)}</div><p class="muted">${esc(m.description)}</p><div class="actions"><span class="helper">${date(m.start_date + "T12:00:00")} · ${quantity(m.days, "день", "дня", "дней")} · ${esc(m.timezone)}</span>${staff ? `<span class="pill">Код: ${esc(m.join_code)}</span><button class="outline small" data-action="copy-code">Копировать код</button>` : ""}</div><div class="tabs" role="navigation" aria-label="Разделы марафона">${[["tasks", "Задания"], ["people", "Участники"], ...(staff || m.published ? [["ranking", "Рейтинг"]] : []), ...(staff ? [["settings", "Настройки"]] : [])].map(([t, l]) => `<button class="${currentTab === t ? "active" : ""}" data-action="tab" data-tab="${t}">${l}</button>`).join("")}</div>${content}`,
  );
}
function rankingSections(rows, renderRows, headingTag = "h2") {
  if (!rows.length) return empty("В марафоне пока нет участников.");
  return Object.entries(rankingCategories)
    .map(([category, heading]) => {
      const group = rows.filter((row) => row.category === category);
      return group.length
        ? `<section class="ranking-group" aria-label="${heading}"><${headingTag}>${heading}</${headingTag}>${renderRows(group, category)}</section>`
        : "";
    })
    .join("");
}
function rankingTable(rows, m) {
  return `<div class="table-wrap"><table><thead><tr><th>Место</th><th>Участник</th>${Array.from({ length: m.days }, (_, i) => `<th>Д${i + 1}</th>`).join("")}<th>Итого</th><th>Выполнено</th><th>Пропущено дней</th><th>Решение</th></tr></thead><tbody>${rows.map((r) => `<tr><td class="rank-num">${r.rank}</td><td><strong>${esc(r.name)}</strong></td>${r.days.map((d) => `<td>${d}</td>`).join("")}<td><strong>${r.score}</strong></td><td>${r.completed_days}</td><td>${r.missed_days}</td><td>${r.tie ? pill("Требуется очная встреча", "bad") : esc(r.decision || "—")}</td></tr>`).join("")}</tbody></table></div>`;
}
async function libraryPage() {
  const libs = await api("/libraries");
  const rows = libs.map((l) => `
    <div class="row">
      <div class="row-main">
        <div class="row-title">${esc(l.name)}</div>
        <div class="row-sub">${esc(competitionLabel(l.competition_category))} · ${stamp(l.created_at)}</div>
      </div>
      <div class="actions">
        <span class="pill">${quantity(l.example_count, "пример", "примера", "примеров")}</span>
        <button class="text-button" data-action="delete-library" data-id="${esc(l.id)}"
          data-name="${esc(l.name)}" aria-label="Удалить файл ${esc(l.name)}">Удалить</button>
      </div>
    </div>`).join("");
  shell(
    `<div class="section-head">
      <div><div class="eyebrow">Материалы для занятий</div><h1>Библиотеки примеров</h1></div>
      <button data-action="upload">Загрузить CSV</button>
    </div>
    <p class="muted">Один файл — одна библиотека. Объединяйте несколько библиотек при генерации заданий.</p>
    <div class="notice">Формат: category, topic, level, expression, answer. Категории BABY / SCHOOL, уровни BASIC / MEDIUM / HARD. <a href="/sample.csv" download>Скачать пример CSV</a></div>
    ${rows || empty("Загрузите первый файл с примерами. Импорт проверит каждую строку.")}`,
    "libraries",
  );
}
async function adminPage() {
  if (user.role !== "ADMIN") throw new Error("Нет доступа");
  const rows = await api("/admin/" + currentAdminTab);
  let html;
  if (currentAdminTab === "users")
    html = `<div class="table-wrap"><table><thead><tr><th>Пользователь</th><th>Почта</th><th>Роль</th><th>Статус</th><th></th></tr></thead><tbody>${rows.map((u) => `<tr><td>${esc(u.full_name)}</td><td>${esc(u.email)}</td><td>${labels[u.role]}</td><td>${u.enabled ? "Активен" : "Отключён"}</td><td>${u.id !== user.id ? `<button class="outline small" data-action="edit-user" data-id="${u.id}" data-role="${u.role}" data-enabled="${u.enabled}">Изменить</button>` : ""}</td></tr>`).join("")}</tbody></table></div>`;
  else if (currentAdminTab === "marathons")
    html = rows.length
      ? `<div class="table-wrap"><table><thead><tr><th>Марафон</th><th>Дата начала</th><th>Статус</th><th></th></tr></thead><tbody>${rows.map((m) => `<tr><td><strong>${esc(m.title)}</strong><div class="helper">${esc(m.description)}</div></td><td>${date(m.start_date + "T12:00:00")}</td><td>${pill(m.effective_status)}</td><td><div class="actions"><a class="button outline small" href="#marathon/${m.id}">Открыть</a><button class="danger small" data-action="delete-marathon" data-id="${esc(m.id)}" data-name="${esc(m.title)}">Удалить</button></div></td></tr>`).join("")}</tbody></table></div>`
      : empty("Марафонов пока нет.");
  else
    html = `<div class="table-wrap"><table><thead><tr>${(currentAdminTab === "audit" ? ["Время", "Кто", "Действие", "Описание"] : ["Время", "Событие", "Статус", "Попыток", "Ошибка"]).map((x) => `<th>${x}</th>`).join("")}</tr></thead><tbody>${rows.map((r) => `<tr>${(currentAdminTab === "audit" ? [stamp(r.at), r.actor_name || "Система", r.action, r.detail] : [stamp(r.created_at), r.event_key, r.status, r.attempts, r.last_error || "—"]).map((x) => `<td>${esc(x)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
  shell(
    `<div class="eyebrow">Администрирование</div><h1>Всё под контролем</h1><div class="tabs">${[
      ["users", "Пользователи"],
      ["marathons", "Марафоны"],
      ["audit", "Журнал действий"],
      ["mail", "Почтовая очередь"],
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
    durationMinutes = t.marathon.taskDurationMinutes || 30,
    done =
      attempt && ["CHECKED", "EXPIRED", "INTERRUPTED"].includes(attempt.status);
  let content = "";
  if (active && parent) {
    content = `<div class="task-header"><div><strong id="answered">0 из ${t.examples.length}</strong><p class="helper" id="save-state" aria-live="polite">Ответы сохраняются автоматически</p></div><div><div class="timer" id="timer" aria-label="Оставшееся время">${String(durationMinutes).padStart(2, "0")}:00</div><span class="helper">до завершения</span></div></div><progress class="progress" id="progress" max="${t.examples.length}" value="0" aria-label="Заполнено ответов"></progress><div id="time-warning" role="status" aria-live="polite"></div><div class="exercises">${t.examples.map((e, i) => `<div class="exercise vertical"><span class="number">${String(i + 1).padStart(2, "0")}</span>${verticalExpression(e.expression, "expr-" + e.id)}<label><span class="visually-hidden">Ответ на пример ${i + 1}: ${esc(e.expression)}</span><input class="answer" data-id="${e.id}" inputmode="text" autocomplete="off" pattern="-?[0-9]*" maxlength="11" value="${esc(e.value ?? "")}" aria-describedby="expr-${e.id}"></label></div>`).join("")}</div><div class="task-footer"><span class="helper">После отправки изменить ответы нельзя.</span><button data-action="submit-task">Завершить и отправить</button></div>`;
  } else if (parent && ["AVAILABLE", "REOPENED"].includes(t.status)) {
    content = `<section class="panel"><h2>${t.status === "REOPENED" ? "Попробуем ещё раз" : "Готовы начать?"}</h2><p>${t.exampleCount} примеров. На работу — ${durationMinutes} минут с момента нажатия кнопки${t.status === "AVAILABLE" ? ", но не позднее закрытия дня" : ""}.</p><p class="muted">Таймер продолжит идти при закрытии страницы. Ответы сохраняются во время работы.</p><button class="orange" data-action="start-task">Начать задание</button></section>`;
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
  const staffActions = parent ? "" : `<div class="actions history">
    ${t.editable ? '<button data-action="edit-assignment">Изменить задание</button>' : ""}
    <button class="outline" data-action="copy-task">Копировать задание</button>
    ${t.child?.deleted_at ? "" : '<button class="outline" data-action="reopen">Повторно открыть после сбоя</button>'}
  </div>`;
  shell(
    `<a class="helper" href="#marathon/${t.marathon.id}">${esc(t.marathon.title)}</a><div class="section-head"><div><div class="eyebrow">В своём темпе, с вниманием к каждому примеру</div><h1>${esc(t.child?.first_name || "")} · день ${a.day}. У тебя получится.</h1></div>${pill(t.status)}</div><p class="muted">${esc(a.instruction || "Впиши ответ в поле рядом с примером.")}</p>${content}${staffActions}`,
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
  return `<div class="table-wrap"><table><thead><tr><th>№</th><th>Пример</th><th>Ответ ребёнка</th><th>Правильный ответ</th><th>Результат</th></tr></thead><tbody>${es.map((e, i) => `<tr><td>${i + 1}</td><td>${verticalExpression(e.expression, "result-expr-" + e.id)}</td><td>${esc(e.value ?? "—")}</td><td>${esc(e.answer ?? "—")}</td><td class="${e.result === "CORRECT" ? "ok" : e.result === "ERROR" ? "wrong" : ""}">${esc(labels[e.result] || "Ещё не проверено")}</td></tr>`).join("")}</tbody></table></div>`;
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
function childGenderField() {
  return `<label>Пол ребёнка<select name="gender" required>
    <option value="">Выберите пол</option>
    <option value="MALE">Мальчик</option>
    <option value="FEMALE">Девочка</option>
  </select></label>`;
}
function birthDateField(child = {}) {
  const today = serverNow().toLocaleDateString("sv-SE", { timeZone: "Europe/Moscow" });
  const year = Number(today.slice(0, 4));
  const earliestYear = Math.min(year - 25, child.birth_year ?? year);
  return field("birthDate", "Дата рождения", "date", child.birth_date ?? "",
    `required min="${earliestYear}-01-01" max="${today}"`);
}
function addChild() {
  modal(
    `<h2>Новый маленький герой</h2><form class="form-grid" data-form="child">${field("lastName", "Фамилия", "text", "", 'required maxlength="80"')}${field("firstName", "Имя", "text", "", 'required maxlength="80"')}${field("middleName", "Отчество · необязательно", "text", "", 'maxlength="80"')}${birthDateField()}${childGenderField()}<label>Категория<select name="category">${option("BABY", "Дошкольник")}${option("SCHOOL", "Школьник")}</select></label><div class="form-error wide" role="alert"></div><button class="wide">Добавить ребёнка</button></form>`,
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
function otpDialog() {
  modal(
    `<h2>Восстановить доступ</h2><p class="muted">Мы отправим запрошенный вами сервисный код. Он действует 10 минут.</p><form class="stack" data-form="otp-request"><input type="hidden" name="purpose" value="RESET">${field("email", "Электронная почта", "email", user?.email || "", 'required maxlength="254" autocomplete="email"')}<div class="form-error" role="alert"></div><button>Получить код</button></form>`,
  );
}
function marathonDialog() {
  modal(
    `<h2>Новый марафон</h2><form class="form-grid" data-form="marathon">${field("title", "Название", "text", "", 'required maxlength="150"')}<label>Дата старта<input type="date" name="startDate" value="${new Date().toLocaleDateString("en-CA")}" required></label>${field("days", "Количество дней", "number", "6", 'required min="1" max="31"')}${field("taskDurationMinutes", "Время на работу, минут", "number", "30", 'required min="1" max="240"')}${field("preschoolExampleCount", "Примеров дошкольникам", "number", "60", 'required min="1" max="200"')}${field("schoolExampleCount", "Примеров школьникам", "number", "100", 'required min="1" max="200"')}${field("timezone", "Часовой пояс", "text", "Europe/Moscow", 'required maxlength="80"')}<label class="wide">Силлабус Excel · необязательно<input name="syllabus" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"></label><p class="helper wide">Если прикрепить силлабус, количество примеров будет взято из него. Файл можно заменить позднее в настройках марафона.</p><label class="wide">Описание<textarea name="description" maxlength="2000"></textarea></label><div class="wide">${check("published", "Публиковать рейтинг для участников", true)}</div><p class="helper wide">Марафон создаётся как черновик. После подготовки заданий опубликуйте его в настройках.</p><div class="form-error wide" role="alert"></div><button class="wide">Создать марафон</button></form>`,
  );
}
async function generateDialog() {
  const libs = await api("/libraries");
  const children = detail.children.filter((child) => !child.deleted_at);
  if (!children.length) {
    toast("Сначала пригласите участников по коду марафона.");
    return;
  }
  const ages = [...new Set(children.map((c) => c.age_at_start).filter(Number.isInteger))]
    .sort((a, b) => a - b);
  const missingBirthDate = children.some((c) => c.age_at_start == null);
  modal(
    `<h2>Индивидуальные задания</h2>
      <p class="helper">Силлабус подбирается по полному возрасту ребёнка на дату начала марафона. Повторы исключаются внутри каждого задания.</p>
      <form class="stack" data-form="generate">
        <fieldset>
          <legend>Участники</legend>
          <div class="form-grid">
          <label>Возраст на дату начала марафона
            <select id="assignment-age-filter" aria-describedby="assignment-age-note">
              ${option("", "Все возрасты")}
              ${ages.map((age) => option(age, ageLabel(age))).join("")}
              ${missingBirthDate ? option("unknown", "Без даты рождения") : ""}
            </select>
          </label>
          <label>Категория соревнования
            <select id="assignment-competition-filter">
              ${option("", "Все категории")}
              ${Object.entries(rankingCategories).map(([category, heading]) =>
                `<optgroup label="${heading}">${competitionsFor(category)
                  .map((item) => option(category + ":" + item.code, item.label)).join("")}</optgroup>`,
              ).join("")}
              ${option("unassigned", "Категория не назначена")}
            </select>
          </label>
          </div>
          <p class="helper" id="assignment-age-note">Возраст рассчитан на ${date(detail.marathon.start_date + "T12:00:00")}.</p>
          <div class="select-list" id="assignment-participants">
            ${children.map((c) => `
              <div data-age="${c.age_at_start ?? "unknown"}" data-group="${c.competition_category ? c.category + ":" + c.competition_category : "unassigned"}">
                <label class="check">
                  <input type="checkbox" name="childIds[]" value="${c.id}" data-age-band="${ageBandFor(c.age_at_start)}" data-age="${c.age_at_start ?? ""}" data-category="${c.category}" data-competition="${esc(c.competition_category)}" checked>
                  <span>${esc(fullName(c))} · ${ageLabel(c.age_at_start)} на старт · ${labels[c.category]} · ${esc(competitionLabel(c.competition_category))}</span>
                </label>
              </div>`).join("")}
          </div>
          <p class="helper" id="assignment-participants-empty" hidden>По выбранным фильтрам участников нет.</p>
        </fieldset>
        <fieldset>
          <legend>Силлабус на один день</legend>
          <div id="assignment-recipes" class="recipe-groups"></div>
          <p class="helper">Итог для этого марафона: дошкольники — ${detail.marathon.preschool_example_count}, школьники — ${detail.marathon.school_example_count}. Строки идут в порядке силлабуса — от простого к сложному.</p>
        </fieldset>
        <fieldset>
          <legend>Библиотеки</legend>
          <div class="select-list" id="assignment-libraries"${libs.length ? "" : " hidden"}>
            ${libs.map((l) => `<div data-competition="${l.competition_category || "unassigned"}"><label class="check"><input type="radio" name="library-${l.competition_category || "unassigned"}" value="${l.id}" data-library data-competition="${l.competition_category || "unassigned"}"><span>${esc(l.name)} · ${esc(competitionLabel(l.competition_category))} · ${l.example_count} примеров</span></label></div>`).join("")}
          </div>
          <p class="helper">Для каждой категории выбранных участников укажите ровно один файл с той же категорией.</p>
          <p class="helper" id="assignment-libraries-empty"${libs.length ? " hidden" : ""}>Для выбранной категории загрузите CSV или Excel в разделе «Библиотеки».</p>
        </fieldset>
        <div class="form-grid">
          ${field("day", "День марафона", "number", "1", `required min="1" max="${detail.marathon.days}"`)}
        </div>
        <label>Инструкция<textarea name="instruction" maxlength="2000">Внимательно прочитай пример и впиши ответ. У тебя всё получится!</textarea></label>
        <p class="helper">У выбранных участников неначатые задания этого дня будут заменены. Начатые задания изменить нельзя.</p>
        <div class="form-error" role="alert"></div>
        <button>Создать задания</button>
      </form>`,
  );
  renderGenerationRecipes($("[data-form=generate]"));
}

function recipeRow(row = { digits: 1, terms: 2, operation: "ADDITION", count: 1 }) {
  return `<div class="recipe-row">
    <label>Разрядность<select data-recipe="digits">${[1, 2, 3, 4, 5, 6].map((v) => option(v, v + "D", row.digits)).join("")}</select></label>
    <label>Чисел в примере<select data-recipe="terms">${[2, 3, 4, 5, 6, 7].map((v) => option(v, v + "R", row.terms)).join("")}</select></label>
    <label>Операция<select data-recipe="operation">${Object.entries(operationLabels).map(([v, label]) => option(v, label, row.operation)).join("")}</select></label>
    <label>Количество<input data-recipe="count" type="number" min="1" max="200" value="${row.count}" required></label>
    <button type="button" class="text-button recipe-remove" data-action="recipe-remove" aria-label="Удалить строку">Удалить</button>
  </div>`;
}

function saveRecipeState(form) {
  form.recipeState ||= new Map();
  for (const panel of $$('[data-recipe-group]', form)) {
    const rows = $$(".recipe-row", panel).map((row) => ({
      digits: +$("[data-recipe=digits]", row).value,
      terms: +$("[data-recipe=terms]", row).value,
      operation: $("[data-recipe=operation]", row).value,
      count: +$("[data-recipe=count]", row).value,
    }));
    form.recipeState.set(panel.dataset.recipeGroup, rows);
  }
}

function selectedGenerationGroups(form) {
  const groups = new Map();
  for (const input of $$('input[name="childIds[]"]:checked:not(:disabled)', form)) {
    const key = [input.dataset.ageBand, input.dataset.category, input.dataset.competition].join(":");
    if (!groups.has(key)) groups.set(key, {
      key,
      ageBand: input.dataset.ageBand,
      category: input.dataset.category,
      competition: input.dataset.competition,
      age: input.dataset.age,
    });
  }
  return [...groups.values()];
}

function renderGenerationRecipes(form) {
  if (!form) return;
  saveRecipeState(form);
  const container = $("#assignment-recipes", form);
  const groups = selectedGenerationGroups(form);
  if (!groups.length) {
    container.innerHTML = empty("Выберите хотя бы одного участника.");
    updateGenerationLibraries(form, groups);
    return;
  }
  container.innerHTML = groups.map((group) => {
    if (!group.ageBand)
      return `<div class="notice error">Для выбранного участника не указана полная дата рождения.</div>`;
    if (!group.competition)
      return `<div class="notice error">Для выбранного участника не назначена категория соревнования.</div>`;
    const rows = form.recipeState?.get(group.key)
      ?? marathonSyllabus(group.ageBand, group.category, group.competition);
    const expected = group.category === "BABY"
      ? detail.marathon.preschool_example_count : detail.marathon.school_example_count;
    return `<section class="recipe-panel" data-recipe-group="${esc(group.key)}" data-age-band="${group.ageBand}" data-category="${group.category}" data-competition="${esc(group.competition)}" data-expected="${expected}">
      <div class="section-head"><div><strong>${ageBandLabels[group.ageBand]} · ${esc(competitionLabel(group.competition))}</strong><p class="helper">${labels[group.category]}</p></div><span class="pill recipe-total"></span></div>
      <div class="recipe-rows">${rows.map(recipeRow).join("")}</div>
      ${rows.length ? "" : '<p class="notice error recipe-empty">В силлабусе нет готовых строк для этой возрастной категории. Добавьте их вручную.</p>'}
      <button type="button" class="outline small" data-action="recipe-add">Добавить строку</button>
    </section>`;
  }).join("");
  updateRecipeTotals(form);
  updateGenerationLibraries(form, groups);
}

function updateRecipeTotals(form) {
  for (const panel of $$('[data-recipe-group]', form)) {
    const total = $$("[data-recipe=count]", panel)
      .reduce((sum, input) => sum + (+input.value || 0), 0);
    const expected = +panel.dataset.expected;
    const pill = $(".recipe-total", panel);
    pill.textContent = `${total} из ${expected}`;
    pill.classList.toggle("bad", total !== expected);
  }
}

function updateGenerationLibraries(form, groups = selectedGenerationGroups(form)) {
  const competitions = new Set(groups.map((group) => group.competition).filter(Boolean));
  const libraries = $("#assignment-libraries", form);
  if (!libraries) return;
  for (const row of $$('div[data-competition]', libraries)) {
    row.hidden = competitions.size > 0 && !competitions.has(row.dataset.competition);
    const checkbox = $("input", row);
    checkbox.disabled = row.hidden;
    if (row.hidden) checkbox.checked = false;
  }
  libraries.hidden = !$("div[data-competition]:not([hidden])", libraries);
  const missing = [...competitions].filter((competition) =>
    !$$('div[data-competition]:not([hidden])', libraries)
      .some((row) => row.dataset.competition === competition));
  const message = $("#assignment-libraries-empty", form);
  message.hidden = !libraries.hidden && !missing.length;
  if (missing.length)
    message.textContent = `Нет файла библиотеки для: ${missing.map(competitionLabel).join(", ")}.`;
}

function collectRecipes(form) {
  const result = [];
  for (const panel of $$('[data-recipe-group]', form)) {
    const rules = $$(".recipe-row", panel).map((row) => ({
      digits: +$("[data-recipe=digits]", row).value,
      terms: +$("[data-recipe=terms]", row).value,
      operation: $("[data-recipe=operation]", row).value,
      count: +$("[data-recipe=count]", row).value,
    }));
    if (!rules.length) throw new Error("Добавьте строки для каждой группы силлабуса.");
    const total = rules.reduce((sum, row) => sum + row.count, 0);
    const expected = +panel.dataset.expected;
    if (total !== expected)
      throw new Error(`${ageBandLabels[panel.dataset.ageBand]} · ${competitionLabel(panel.dataset.competition)}: должно быть ${expected} примеров, сейчас ${total}.`);
    result.push({
      ageBand: panel.dataset.ageBand,
      category: panel.dataset.category,
      competitionCategory: panel.dataset.competition,
      rules,
    });
  }
  if (!result.length) throw new Error("Добавьте правила генерации.");
  return result;
}
async function manualDialog() {
  const mid = currentMarathon;
  const schedule = await api("/marathons/" + mid);
  if (location.hash !== "#marathon/" + mid || currentTab !== "tasks") return;
  const children = schedule.children.filter((child) => !child.deleted_at);
  if (!children.length) {
    toast("Сначала пригласите участников.");
    return;
  }
  const days = schedule.assignableDays;
  if (!days.length) {
    toast("В этом марафоне нет открытых или будущих дней для назначения заданий.");
    return;
  }
  const started = new Set(schedule.assignments.filter((a) => a.current_attempt)
    .map((a) => a.child_id + ":" + a.day));
  const daysByChild = new Map(children.map((c) => [
    c.id, days.filter((day) => !started.has(c.id + ":" + day)),
  ]));
  const firstChild = children.find((c) => daysByChild.get(c.id).includes(days[0]))
    || children.find((c) => daysByChild.get(c.id).length)
    || children[0];
  modal(
    `<h2>Задание вручную</h2>
      <form class="stack" data-form="manual">
        <label>Ребёнок<select name="childId">${children.map((c) => option(c.id, fullName(c), firstChild.id)).join("")}</select></label>
        <label>День марафона<select name="day" required></select></label>
        <label>Примеры: выражение = ответ<textarea name="lines" required rows="7" placeholder="25 + 14 - 7 = 32&#10;9 - 12 = -3"></textarea></label>
        <p class="helper">По одному примеру на строку. Разделитель — последний знак =. Только целые ответы.</p>
        <label>Инструкция<textarea name="instruction" maxlength="2000"></textarea></label>
        <div class="form-error" role="alert"></div>
        <button type="submit">Назначить</button>
      </form>`,
  );
  const form = $('[data-form="manual"]', $("#dialog"));
  const day = form.elements.day;
  const updateDays = () => {
    const available = daysByChild.get(form.elements.childId.value);
    const selected = Number(day.value);
    day.innerHTML = available.map((d) => option(d, "День " + d, selected)).join("")
      || option("", "Нет доступных дней для этого ребёнка");
    day.disabled = !available.length;
    $("button[type=submit]", form).disabled = day.disabled;
  };
  form.elements.childId.addEventListener("change", updateDays);
  updateDays();
}
function parseExampleLines(lines) {
  return lines.split("\n").flatMap((line, index) => {
    if (!line.trim()) return [];
    const at = line.lastIndexOf("=");
    const expression = line.slice(0, at).trim();
    const answer = line.slice(at + 1).trim();
    if (at < 1 || !expression || !answer)
      throw new Error("Строка " + (index + 1) + ": введите выражение и ответ через знак =.");
    if (!/^-?\d+$/.test(answer))
      throw new Error("Строка " + (index + 1) + ": ответ должен быть целым числом.");
    return [{ expression, answer }];
  });
}
function editAssignmentDialog() {
  if (!task?.editable) {
    toast("Ребёнок уже открыл задание. Изменять его нельзя.");
    return;
  }
  const lines = task.examples
    .map((example) => `${example.expression} = ${example.answer}`)
    .join("\n");
  modal(`<h2>Изменить задание</h2>
    <p class="helper">${esc(fullName(task.child))} · день ${task.assignment.day}. Изменения разрешены только до начала выполнения ребёнком.</p>
    <form class="stack" data-form="assignment-edit">
      <label>Примеры: выражение = ответ<textarea name="lines" required rows="14">${esc(lines)}</textarea></label>
      <p class="helper">Можно исправлять, добавлять и удалять строки. От 1 до 200 примеров.</p>
      <label>Инструкция<textarea name="instruction" maxlength="2000">${esc(task.assignment.instruction || "")}</textarea></label>
      <div class="form-error" role="alert"></div>
      <button>Сохранить изменения</button>
    </form>`);
}
async function copyDialog() {
  detail = await api("/marathons/" + task.marathon.id);
  const children = detail.children.filter((child) => !child.deleted_at);
  if (!children.length) {
    toast("В марафоне нет детей, которым можно назначить задание.");
    return;
  }
  modal(
    `<h2>Копировать задание</h2><form class="stack" data-form="copy"><label>Ребёнок<select name="childId">${childOptions(children)}</select></label>${field("day", "День", "number", task.assignment.day, `required min="1" max="${detail.marathon.days}"`)}<div class="form-error" role="alert"></div><button>Копировать</button></form>`,
  );
}
async function tieDialog(category, score) {
  const rows = await api("/marathons/" + currentMarathon + "/ranking");
  const group = rows.filter((r) => r.category === category && r.score === score);
  if (group.length < 2) {
    toast("Результаты изменились. Откройте рейтинг ещё раз.");
    return;
  }
  modal(
    `<h2>Решение очной встречи</h2><p class="helper">${rankingCategories[category]} · ${score} б. Укажите порядок участников в группе.</p><form class="stack" data-form="tie"><div class="select-list">${group.map((r, i) => `<label>${esc(r.name)}<input type="number" name="rank-${r.child_id}" min="1" max="${group.length}" ${r.decision ? `value="${i + 1}"` : ""} placeholder="Место в группе" required></label>`).join("")}</div><label>Дата, основание и решение<textarea name="reason" required minlength="10" maxlength="1000">${esc(group[0].decision)}</textarea></label><div class="form-error" role="alert"></div><button>Зафиксировать места</button></form>`,
  );
}
async function addTeacherDialog() {
  const users = await api("/admin/users");
  const assigned = new Set(detail.teachers.map((teacher) => teacher.id));
  const available = users.filter((u) =>
    u.enabled && ["TEACHER", "ADMIN"].includes(u.role) && !assigned.has(u.id));
  if (!available.length) {
    toast("Все доступные педагоги уже назначены на этот марафон.");
    return;
  }
  modal(
    `<h2>Добавить педагогов</h2><p class="helper">Можно выбрать сразу несколько человек. Каждый получит полный доступ к информации обо всех учениках этого марафона.</p><form class="stack" data-form="teacher"><div class="select-list">${available
      .map((u) => `<label class="check"><input type="checkbox" name="userIds[]" value="${esc(u.id)}"><span>${esc(u.full_name)}</span></label>`)
      .join("")}</div><div class="form-error" role="alert"></div><button>Добавить выбранных</button></form>`,
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
    `<main id="main" class="legal"><a class="brand" href="#home">Считай.</a><h1 class="history">Документы и согласия</h1><p>Версия от 19 ноября 2026 года. Оператор: ${esc(config.operator)}. Контакт для обращений: ${esc(config.contact)}.</p><h2>Условия использования</h2><p>Платформа проводит образовательные марафоны. Родитель действует как законный представитель ребёнка, указывает достоверные сведения и обеспечивает доступ ребёнка только через свой аккаунт. Организатор определяет расписание, задания и порядок очной встречи при равных баллах. Один верный ответ приносит один балл; ошибки и пропуски учитываются отдельно.</p><h2>Согласие на обработку данных</h2><p>Отдельной отметкой при регистрации родитель разрешает оператору обрабатывать свои имя, фамилию и адрес электронной почты, а также ФИО, дату или год рождения, пол, категорию, ответы и результаты ребёнка для регистрации, проведения марафона, проверки работ и связи по вопросам участия. Операции: сбор, запись, хранение, уточнение, использование и удаление с применением автоматизации. Пароль хранится в виде защищённого хеша.</p><p>Согласие действует до отзыва. Для отзыва обязательного согласия, получения копии данных или удаления аккаунта обратитесь к оператору по указанному контакту. После отзыва участие прекращается; оператор удаляет данные, кроме подлежащих обязательному хранению. В резервных копиях удаление происходит при плановом истечении их срока хранения.</p><h2>Согласие на участие</h2><p>Отдельной отметкой законный представитель разрешает ребёнку участвовать в марафонах и подтверждает правила выполнения: ограничение времени, автоматическая проверка, невозможность редактировать отправленную работу, повторное открытие только после подтверждённого технического сбоя.</p><h2>Письма — по желанию</h2><p>Отдельная необязательная отметка разрешает отправлять письма о ходе марафона, включая открытие задания. Сервисные коды восстановления пароля отправляются только по отдельному запросу пользователя.</p><h2>Публикация рейтинга — по желанию</h2><p>Отдельная необязательная отметка разрешает показывать полные ФИО, категорию и результаты ребёнка авторизованным участникам этого марафона после публикации рейтинга организатором. При отказе или отзыве ФИО заменяются подписью «Участник без публикации ФИО». Педагог и администратор видят данные для организации обучения.</p><h2>Хранение и безопасность</h2><p>Используются серверная проверка доступа, защищённые сессии, журнал действий и резервное копирование. Технические сессии необходимы для работы сайта. Данные не используются для рекламы. Вопросы обработки данных и удаления направляйте оператору.</p><a class="button" href="#home">Вернуться в кабинет</a></main>`;
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
      case "child-birth-date": {
        const child = dashboard.children.find((item) => item.id === el.dataset.id);
        if (!child) return;
        modal(`<h2>Дата рождения ребёнка</h2><form class="stack" data-form="child-birth-date">
          <p>${esc(fullName(child))}${child.birth_date ? "" : ` · ${child.birth_year} год рождения`}</p>
          <input type="hidden" name="id" value="${esc(child.id)}">
          ${birthDateField(child)}
          <div class="form-error" role="alert"></div>
          <button>Сохранить</button>
        </form>`);
        break;
      }
      case "child-gender":
        modal(`<h2>Указать пол ребёнка</h2><form class="stack" data-form="child-gender">
          <input type="hidden" name="id" value="${esc(el.dataset.id)}">
          ${childGenderField()}
          <div class="form-error" role="alert"></div>
          <button>Сохранить</button>
        </form>`);
        break;
      case "delete-child": {
        const child = dashboard.children.find((item) => item.id === el.dataset.id);
        if (!child) return;
        modal(`<h2>Удалить карточку ребёнка?</h2>
          <p><strong>${esc(fullName(child))}</strong> исчезнет из вашего кабинета. Задания ребёнка станут недоступны, а результаты сохранятся у педагога.</p>
          <form class="stack" data-form="delete-child">
            <input type="hidden" name="id" value="${esc(child.id)}">
            <div class="form-error" role="alert"></div>
            <div class="actions">
              <button type="button" class="outline" data-action="close" autofocus>Отмена</button>
              <button type="submit" class="danger">Удалить карточку</button>
            </div>
          </form>`);
        break;
      }
      case "join":
        await joinDialog();
        break;
      case "reset":
        otpDialog();
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
        nav("marathon/" + currentMarathon);
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
        await manualDialog();
        break;
      case "edit-assignment":
        editAssignmentDialog();
        break;
      case "copy-task":
        await copyDialog();
        break;
      case "tie":
        await tieDialog(el.dataset.category, Number(el.dataset.score));
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
          `<h2>Новая библиотека</h2><form class="stack" data-form="upload">${field("name", "Название библиотеки", "text", "", 'required maxlength="120"')}<label>Категория соревнования<select name="competitionCategory">${option("", "Без категории соревнования")}${competitionOptions()}</select></label><label>Файл CSV или Excel<input name="file" type="file" accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" required></label><p class="helper">Excel: до 10 МБ, листы вида 1D2R, 2D×1D или 3D÷1D и парные листы с ответами. Для Excel обязательно выберите категорию соревнования. CSV остаётся доступен в прежнем формате. <a href="/sample.csv" download>Скачать образец CSV</a></p><div class="form-error" role="alert"></div><button>Проверить и загрузить</button></form>`,
        );
        break;
      case "recipe-add": {
        const panel = el.closest("[data-recipe-group]");
        $(".recipe-rows", panel).insertAdjacentHTML("beforeend", recipeRow());
        $(".recipe-empty", panel)?.remove();
        updateRecipeTotals(el.closest("form"));
        break;
      }
      case "recipe-remove": {
        const form = el.closest("form");
        el.closest(".recipe-row").remove();
        updateRecipeTotals(form);
        break;
      }
      case "delete-library":
        modal(`<h2>Удалить файл из библиотеки?</h2>
          <p>Файл «${esc(el.dataset.name)}» исчезнет из списка источников для генерации. В созданных заданиях примеры сохранятся.</p>
          <form class="stack" data-form="delete-library">
            <input type="hidden" name="id" value="${esc(el.dataset.id)}">
            <div class="form-error" role="alert"></div>
            <div class="actions">
              <button type="button" class="outline" data-action="close" autofocus>Отмена</button>
              <button type="submit" class="danger">Удалить файл</button>
            </div>
          </form>`);
        break;
      case "delete-marathon":
        modal(`<h2>Удалить марафон?</h2>
          <p>Марафон «${esc(el.dataset.name)}» будет удалён без возможности восстановления.</p>
          <div class="notice error">Будут удалены задания, попытки, ответы, результаты, силлабус и список участников этого марафона. Аккаунты пользователей и карточки детей сохранятся.</div>
          <form class="stack" data-form="delete-marathon">
            <input type="hidden" name="id" value="${esc(el.dataset.id)}">
            <div class="form-error" role="alert"></div>
            <div class="actions">
              <button type="button" class="outline" data-action="close" autofocus>Отмена</button>
              <button type="submit" class="danger">Удалить марафон навсегда</button>
            </div>
          </form>`);
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
          `<h2>Повторное открытие</h2><form class="stack" data-form="reopen"><label>Причина технического сбоя<textarea name="reason" minlength="10" maxlength="1000" required></textarea></label>${check("confirmed", "Технический сбой подтверждён. Я понимаю, что действие будет записано в журнал.", false, true)}<p class="helper">Предыдущая попытка сохранится. На новую попытку будет отведено ${task.marathon.taskDurationMinutes || 30} минут; начать нужно в течение 24 часов.</p><div class="form-error" role="alert"></div><button>Повторно открыть</button></form>`,
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
        // The login form may still be open after the server or another tab ended the session.
        csrf = (await api("/session")).csrfToken;
        await api("/login", "POST", b);
        await session();
        nav("home");
        break;
      case "register":
        await api("/register", "POST", b);
        await api("/login", "POST", { email: b.email, password: b.password });
        await session();
        nav("home");
        toast("Аккаунт создан");
        break;
      case "child":
        await api("/children", "POST", b);
        close();
        await render();
        toast("Профиль ребёнка добавлен");
        break;
      case "child-birth-date":
        await api("/children/" + b.id + "/birth-date", "PATCH", { birthDate: b.birthDate });
        close();
        await render();
        toast("Дата рождения сохранена");
        break;
      case "child-gender":
        await api("/children/" + b.id + "/gender", "PATCH", { gender: b.gender });
        close();
        await render();
        break;
      case "delete-child":
        await api("/children/" + b.id, "DELETE");
        if (currentChild === b.id) currentChild = "";
        close();
        await render();
        $('#app [data-action="add-child"]')?.focus();
        toast("Карточка ребёнка удалена");
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
          `<h2>Введите код из письма</h2><p class="helper">Если адрес зарегистрирован, код отправлен. В локальном режиме сообщение находится в тестовой очереди.</p><form class="stack" data-form="otp-verify"><input type="hidden" name="email" value="${esc(b.email)}"><input type="hidden" name="purpose" value="RESET">${field("code", "Код из 6 цифр", "text", "", 'required pattern="[0-9]{6}" maxlength="6" inputmode="numeric" autocomplete="one-time-code"')}${field("newPassword", "Новый пароль", "password", "", 'required minlength="10" autocomplete="new-password"')}<div class="form-error" role="alert"></div><button>Подтвердить</button></form>`;
        break;
      case "otp-verify":
        await api("/otp/verify", "POST", b);
        close();
        await session();
        await render();
        toast("Пароль изменён. Войдите снова.");
        break;
      case "marathon": {
        const syllabus = f.elements.syllabus.files[0];
        delete b.syllabus;
        const m = await api("/marathons", "POST", b);
        if (syllabus) {
          const upload = new FormData();
          upload.append("file", syllabus);
          await api(`/marathons/${m.id}/syllabus`, "POST", upload);
        }
        close();
        currentTab = "settings";
        nav("marathon/" + m.id);
        break;
      }
      case "settings":
        if (b.startDate === f.elements.startDate.defaultValue) delete b.startDate;
        await api("/marathons/" + currentMarathon, "PATCH", b);
        await marathonPage(currentMarathon);
        toast("Настройки сохранены");
        break;
      case "syllabus":
        await api(`/marathons/${currentMarathon}/syllabus`, "POST", new FormData(f));
        await marathonPage(currentMarathon);
        toast("Силлабус прикреплён. Порядок и количество примеров обновлены");
        break;
      case "competition-category":
        await api(`/marathons/${currentMarathon}/participants/${b.childId}/competition-category`,
          "PATCH", { competitionCategory: b.competitionCategory });
        toast("Категория соревнования сохранена");
        break;
      case "generate":
        b.childIds = new FormData(f).getAll("childIds[]");
        b.libraryIds = $$('input[data-library]:checked', f).map((input) => input.value);
        if (!b.childIds.length) throw new Error("Выберите участников.");
        const participantCategories = new Set(
          $$('input[name="childIds[]"]:checked:not(:disabled)', f)
            .map((input) => input.dataset.competition).filter(Boolean));
        for (const category of participantCategories) {
          const matching = $$('input[data-library]:checked', f)
            .filter((input) => input.dataset.competition === category);
          if (matching.length !== 1)
            throw new Error(`Для категории «${competitionLabel(category)}» выберите ровно один файл библиотеки.`);
        }
        b.recipes = collectRecipes(f);
        await api("/marathons/" + currentMarathon + "/generate", "POST", b);
        close();
        currentTab = "tasks";
        await marathonPage(currentMarathon);
        toast("Индивидуальные задания созданы");
        break;
      case "manual":
        if (!b.day) throw new Error("Выберите день марафона для этого ребёнка.");
        b.examples = parseExampleLines(b.lines);
        await api("/marathons/" + currentMarathon + "/assignments", "POST", b);
        close();
        currentTab = "tasks";
        await marathonPage(currentMarathon);
        toast("Задание назначено");
        break;
      case "assignment-edit":
        b.examples = parseExampleLines(b.lines);
        await api("/assignments/" + task.assignment.id, "PUT", b);
        close();
        task = await api("/assignments/" + task.assignment.id);
        renderTask();
        toast("Изменения сохранены");
        break;
      case "upload":
        await api("/libraries", "POST", new FormData(f));
        close();
        await libraryPage();
        toast("Библиотека проверена и загружена");
        break;
      case "delete-library":
        await api("/libraries/" + b.id, "DELETE");
        close();
        await libraryPage();
        $('[data-action="upload"]')?.focus();
        toast("Файл удалён из библиотеки");
        break;
      case "delete-marathon":
        await api("/marathons/" + b.id, "DELETE");
        if (currentMarathon === b.id) currentMarathon = "";
        close();
        await adminPage();
        toast("Марафон и связанные данные удалены");
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
        b.userIds = new FormData(f).getAll("userIds[]");
        if (!b.userIds.length) throw new Error("Выберите хотя бы одного педагога.");
        await api("/marathons/" + currentMarathon + "/teachers", "POST", b);
        close();
        await marathonPage(currentMarathon);
        toast("Педагоги добавлены к марафону");
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
    if (button && button.isConnected)
      button.disabled = f.dataset.form === "manual" && f.elements.day.disabled;
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
  if (e.target.id === "assignment-age-filter" || e.target.id === "assignment-competition-filter") {
    const form = e.target.form;
    const age = $("#assignment-age-filter", form).value;
    const group = $("#assignment-competition-filter", form).value;
    const participants = $("#assignment-participants", form);
    for (const row of $$("[data-age]", participants)) {
      const matches = (!age || row.dataset.age === age)
        && (!group || row.dataset.group === group);
      const checkbox = $("input", row);
      row.hidden = !matches;
      checkbox.disabled = !matches;
      checkbox.checked = matches;
    }
    participants.hidden = !$("div[data-age]:not([hidden])", participants);
    $("#assignment-participants-empty", form).hidden = !participants.hidden;
    participants.scrollTop = 0;
    renderGenerationRecipes(form);
  }
  if (e.target.matches('input[name="childIds[]"]'))
    renderGenerationRecipes(e.target.form);
  if (e.target.matches("[data-recipe]")) updateRecipeTotals(e.target.form);
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
