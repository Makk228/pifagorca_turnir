package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Assignments {
  final Db db;
  final Auth auth;
  final Marathons marathons;
  final Libraries libraries;
  final Clock clock;

  Assignments(Db db, Auth auth, Marathons marathons, Libraries libraries, Clock clock) {
    this.db = db;
    this.auth = auth;
    this.marathons = marathons;
    this.libraries = libraries;
    this.clock = clock;
  }

  Map<String, Object> access(Map<String, Object> u, String aid, boolean write) {
    var a = db.one("select * from assignments where id=? for update", aid);
    marathons.access(u, s(a, "marathon_id"), write);
    if (s(u, "role").equals("PARENT")) marathons.ownChild(u, s(a, "child_id"));
    return a;
  }

  @Transactional
  public List<String> generate(Map<String, Object> u, String mid, Map<String, Object> body) {
    var m = marathons.lock(u, mid, true);
    int day = number(body, "day", 1, n(m, "days"));
    var lids = ids(body, "libraryIds");
    var childIds = ids(body, "childIds");
    var competitionCategories = new LinkedHashSet<String>();
    for (String childId : childIds) {
      var participant = db.maybe(
          "select e.competition_category from enrollments e where e.marathon_id=? and e.child_id=?",
          mid, childId);
      require(participant.isPresent(), "Ребёнок не участвует в марафоне");
      String competition = s(participant.orElseThrow(), "competition_category");
      require(!competition.isBlank(), "Сначала назначьте ребёнку категорию соревнования");
      competitionCategories.add(competition);
    }
    var libraryByCompetition =
        libraries.requireOnePerCompetitionCategory(u, lids, competitionCategories);
    if (body.get("recipes") instanceof List<?>
        || db.maybe("select marathon_id from marathon_syllabus_rules where marathon_id=? limit 1", mid)
            .isPresent())
      return generateByRecipes(u, m, body, childIds, libraryByCompetition, day);
    int count = number(body, "count", 1, 200);
    String level = optional(body, "level", 10), topic = optional(body, "topic", 80);
    if (!level.isEmpty()) choice(body, "level", "BASIC", "MEDIUM", "HARD");
    var result = new ArrayList<String>();
    for (String cid : childIds) {
      var participant = db.maybe(
          "select c.category,e.competition_category from enrollments e join children c on c.id=e.child_id"
              + " where e.marathon_id=? and e.child_id=?",
          mid, cid);
      require(participant.isPresent(), "Ребёнок не участвует в марафоне");
      var c = participant.orElseThrow();
      var examples = libraries.select(
          u,
          List.of(libraryByCompetition.get(s(c, "competition_category"))),
          s(c, "category"),
          s(c, "competition_category"),
          level,
          topic,
          count);
      result.add(assign(u, m, cid, day, optional(body, "instruction", 2000), examples));
    }
    return result;
  }

  private List<String> generateByRecipes(
      Map<String, Object> u,
      Map<String, Object> marathon,
      Map<String, Object> body,
      List<String> childIds,
      Map<String, String> libraryByCompetition,
      int day) {
    var recipes = recipes(body, s(marathon, "id"));
    var prepared = new ArrayList<Map.Entry<String, List<Map<String, Object>>>>();
    for (String childId : childIds) {
      var child = db.maybe(
          "select c.category,c.birth_date,e.competition_category from enrollments e"
              + " join children c on c.id=e.child_id where e.marathon_id=? and e.child_id=?",
          s(marathon, "id"), childId);
      require(child.isPresent(), "Ребёнок не участвует в марафоне");
      var participant = child.orElseThrow();
      require(participant.get("birth_date") instanceof LocalDate,
          "У ребёнка должна быть указана полная дата рождения");
      String category = s(participant, "category");
      String competition = s(participant, "competition_category");
      require(!competition.isBlank(), "Сначала назначьте ребёнку категорию соревнования");
      int age = Period.between((LocalDate) participant.get("birth_date"),
          (LocalDate) marathon.get("start_date")).getYears();
      require(age >= 0, "Дата рождения ребёнка позже даты начала марафона");
      String ageBand = ageBand(age);
      var rules = recipes.get(recipeKey(ageBand, category, competition));
      require(rules != null,
          "Не настроен силлабус для возраста " + ageBandLabel(ageBand) + " · "
              + CompetitionCategory.label(competition));
      int expected = category.equals("BABY")
          ? n(marathon, "preschool_example_count") : n(marathon, "school_example_count");
      require(rules.stream().mapToInt(Libraries.Rule::count).sum() == expected,
          "Для " + ageBandLabel(ageBand) + " нужно ровно " + expected + " примеров");
      prepared.add(Map.entry(childId,
          libraries.selectRules(
              u, List.of(libraryByCompetition.get(competition)), category, competition, rules)));
    }
    var result = new ArrayList<String>();
    String instruction = optional(body, "instruction", 2000);
    for (var child : prepared)
      result.add(assign(u, marathon, child.getKey(), day, instruction, child.getValue()));
    return result;
  }

  private Map<String, List<Libraries.Rule>> recipes(Map<String, Object> body, String marathonId) {
    var result = new HashMap<String, List<Libraries.Rule>>();
    if (!(body.get("recipes") instanceof List<?>)) {
      for (var row : db.list(
          "select * from marathon_syllabus_rules where marathon_id=?"
              + " order by age_band,category,competition_category,position", marathonId))
        result.computeIfAbsent(
                recipeKey(s(row, "age_band"), s(row, "category"), s(row, "competition_category")),
                ignored -> new ArrayList<>())
            .add(new Libraries.Rule(n(row, "digits"), n(row, "terms"),
                s(row, "operation"), n(row, "example_count")));
      require(!result.isEmpty(), "К марафону не прикреплён силлабус");
      return result;
    }
    for (Object item : (List<?>) body.get("recipes")) {
      require(item instanceof Map<?, ?>, "Некорректный силлабус");
      @SuppressWarnings("unchecked")
      var recipe = (Map<String, Object>) item;
      String ageBand = choice(recipe, "ageBand", "4_5", "6_7", "8_9", "10_11", "12_PLUS");
      String category = choice(recipe, "category", "BABY", "SCHOOL");
      String competition = text(recipe, "competitionCategory", 24);
      CompetitionCategory.validate(competition, category);
      require(recipe.get("rules") instanceof List<?>, "Добавьте строки силлабуса");
      var rules = new ArrayList<Libraries.Rule>();
      for (Object row : (List<?>) recipe.get("rules")) {
        require(row instanceof Map<?, ?>, "Некорректная строка силлабуса");
        @SuppressWarnings("unchecked")
        var rule = (Map<String, Object>) row;
        int digits = number(rule, "digits", 1, 6), terms = number(rule, "terms", 2, 7);
        int count = number(rule, "count", 1, 200);
        String operation = choice(rule, "operation",
            "ADDITION", "SUBTRACTION", "MULTIPLICATION", "DIVISION");
        rules.add(new Libraries.Rule(digits, terms, operation, count));
      }
      require(!rules.isEmpty(), "Добавьте хотя бы одну строку силлабуса");
      require(result.put(recipeKey(ageBand, category, competition), rules) == null,
          "Одна группа силлабуса передана дважды");
    }
    require(!result.isEmpty(), "Добавьте правила генерации");
    return result;
  }

  private String recipeKey(String ageBand, String category, String competition) {
    return ageBand + ":" + category + ":" + competition;
  }

  private String ageBand(int age) {
    if (age <= 5) return "4_5";
    if (age <= 7) return "6_7";
    if (age <= 9) return "8_9";
    if (age <= 11) return "10_11";
    return "12_PLUS";
  }

  private String ageBandLabel(String ageBand) {
    return switch (ageBand) {
      case "4_5" -> "4–5 лет";
      case "6_7" -> "6–7 лет";
      case "8_9" -> "8–9 лет";
      case "10_11" -> "10–11 лет";
      default -> "12+";
    };
  }

  @Transactional
  public String manual(Map<String, Object> u, String mid, Map<String, Object> body) {
    var m = marathons.lock(u, mid, true);
    var examples = examples(body);
    return assign(
        u,
        m,
        text(body, "childId", 36),
        number(body, "day", 1, n(m, "days")),
        optional(body, "instruction", 2000),
        examples);
  }

  @Transactional
  public void update(Map<String, Object> u, String aid, Map<String, Object> body) {
    var assignment = access(u, aid, true);
    var marathon = marathons.lock(u, s(assignment, "marathon_id"), true);
    require(assignment.get("current_attempt") == null,
        "Ребёнок уже открыл задание. Изменять его нельзя");
    require(!Set.of("CANCELLED", "COMPLETED").contains(marathons.effectiveStatus(marathon)),
        "Марафон закрыт");
    require(clock.instant().isBefore(marathons.opens(marathon, n(assignment, "day") + 1)),
        "Этот день уже закрыт");
    var examples = examples(body);
    db.run("delete from examples where assignment_id=?", aid);
    int position = 0;
    for (var example : examples)
      db.run(
          "insert into examples values(?,?,?,?,?)",
          id(), aid, ++position, s(example, "expression"), n(example, "answer"));
    db.run("update assignments set instruction=? where id=?",
        optional(body, "instruction", 2000), aid);
    auth.audit(s(u, "id"), "ASSIGNMENT_UPDATED", aid,
        "Примеров: " + examples.size());
  }

  private List<Map<String, Object>> examples(Map<String, Object> body) {
    require(body.get("examples") instanceof List<?>, "Добавьте примеры");
    var list = (List<?>) body.get("examples");
    require(!list.isEmpty() && list.size() <= 200, "Нужно от 1 до 200 примеров");
    List<Map<String, Object>> examples = new ArrayList<>();
    for (Object obj : list) {
      require(obj instanceof Map<?, ?>, "Некорректный пример");
      @SuppressWarnings("unchecked")
      var row = (Map<String, Object>) obj;
      examples.add(
          Map.of(
              "expression",
              text(row, "expression", 200),
              "answer",
              number(row, "answer", Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }
    return examples;
  }

  String assign(
      Map<String, Object> u,
      Map<String, Object> m,
      String cid,
      int day,
      String instruction,
      List<Map<String, Object>> examples) {
    require(
        !Set.of("CANCELLED", "COMPLETED").contains(marathons.effectiveStatus(m)), "Марафон закрыт");
    require(clock.instant().isBefore(marathons.opens(m, day + 1)), "Этот день уже закрыт");
    require(
        db.maybe(
                "select child_id from enrollments where marathon_id=? and child_id=?",
                s(m, "id"),
                cid)
            .isPresent(),
        "Ребёнок не участвует в марафоне");
    requireActiveChild(cid);
    var existing =
        db.maybe(
            "select * from assignments where marathon_id=? and child_id=? and day=? for update",
            s(m, "id"),
            cid,
            day);
    String aid;
    if (existing.isPresent()) {
      var a = existing.get();
      require(a.get("current_attempt") == null, "Начатое задание нельзя менять");
      aid = s(a, "id");
      db.run("delete from examples where assignment_id=?", aid);
      db.run("update assignments set instruction=? where id=?", instruction, aid);
    } else {
      aid = id();
      db.run(
          "insert into assignments(id,marathon_id,child_id,day,instruction) values(?,?,?,?,?)",
          aid,
          s(m, "id"),
          cid,
          day,
          instruction);
    }
    int pos = 0;
    for (var e : examples)
      db.run(
          "insert into examples values(?,?,?,?,?)",
          id(),
          aid,
          ++pos,
          s(e, "expression"),
          n(e, "answer"));
    auth.audit(
        s(u, "id"), "ASSIGNMENT_CREATED", aid, "День " + day + ", примеров " + examples.size());
    return aid;
  }

  @Transactional
  public String copy(Map<String, Object> u, String aid, Map<String, Object> body) {
    var source = db.one("select * from assignments where id=?", aid);
    var m = marathons.lock(u, s(source, "marathon_id"), true);
    return assign(
        u,
        m,
        text(body, "childId", 36),
        number(body, "day", 1, n(m, "days")),
        s(source, "instruction"),
        db.list(
            "select expression,answer from examples where assignment_id=? order by position", aid));
  }

  @Transactional
  public Map<String, Object> get(Map<String, Object> u, String aid) {
    var a = access(u, aid, false);
    expire(a);
    return view(u, a);
  }

  @Transactional
  public Map<String, Object> start(Map<String, Object> u, String aid) {
    auth.parent(u);
    var source = db.one("select marathon_id from assignments where id=?", aid);
    var m = marathons.lock(u, s(source, "marathon_id"), false);
    var a = db.one("select * from assignments where id=? for update", aid);
    marathons.ownChild(u, s(a, "child_id"));
    require(
        !Set.of("DRAFT", "CANCELLED").contains(marathons.effectiveStatus(m)), "Марафон недоступен");
    expire(a);
    boolean reopened =
        a.get("reopened_until") != null && time(a, "reopened_until").isAfter(clock.instant());
    if (a.get("current_attempt") != null && !reopened) return view(u, a);
    Instant now = clock.instant();
    if (!reopened)
      require(
          !now.isBefore(marathons.opens(m, n(a, "day")))
              && now.isBefore(marathons.opens(m, n(a, "day") + 1)),
          "Задание сейчас недоступно");
    int durationMinutes = n(m, "task_duration_minutes");
    Instant deadline = now.plusSeconds(durationMinutes * 60L);
    if (!reopened && deadline.isAfter(marathons.opens(m, n(a, "day") + 1)))
      deadline = marathons.opens(m, n(a, "day") + 1);
    String tid = id();
    db.run(
        "insert into attempts(id,assignment_id,started_at,deadline,status)"
            + " values(?,?,?,?,'IN_PROGRESS')",
        tid,
        aid,
        now,
        deadline);
    if (reopened && a.get("current_attempt") != null) {
      db.run(
          "insert into answers(attempt_id,example_id,value) select ?,example_id,value from answers"
              + " where attempt_id=?",
          tid,
          a.get("current_attempt"));
    }
    db.run("update assignments set current_attempt=?,reopened_until=null where id=?", tid, aid);
    a.put("current_attempt", tid);
    a.put("reopened_until", null);
    invalidate(s(a, "marathon_id"));
    auth.audit(s(u, "id"), "ATTEMPT_STARTED", aid, tid);
    notifyParent(a, "started:" + tid,
        "Задание начато. На выполнение — до " + durationMinutes + " минут.");
    return view(u, a);
  }

  @Transactional
  public Map<String, Object> save(
      Map<String, Object> u, String aid, Map<String, Object> body, boolean submit) {
    auth.parent(u);
    var a = access(u, aid, false);
    var m = marathons.access(u, s(a, "marathon_id"), false);
    require(!s(m, "status").equals("CANCELLED"), "Марафон отменён");
    require(a.get("current_attempt") != null, "Сначала начните задание");
    expire(a);
    var t = db.one("select * from attempts where id=?", a.get("current_attempt"));
    if (!s(t, "status").equals("IN_PROGRESS")) return view(u, a);
    require(a.get("reopened_until") == null, "Начните новую попытку");
    int version = number(body, "version", 0, Integer.MAX_VALUE);
    if (version != n(t, "version"))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Ответы изменены на другом устройстве. Обновите страницу");
    require(body.get("answers") instanceof Map<?, ?>, "Нужен объект answers");
    var values = (Map<?, ?>) body.get("answers");
    Set<String> allowed = new HashSet<>();
    db.list("select id from examples where assignment_id=?", aid)
        .forEach(e -> allowed.add(s(e, "id")));
    require(values.size() <= allowed.size(), "Слишком много ответов");
    Map<String, Integer> parsed = new LinkedHashMap<>();
    for (var e : values.entrySet()) {
      String eid = e.getKey().toString();
      require(allowed.contains(eid), "Пример не принадлежит заданию");
      String raw = Objects.toString(e.getValue(), "").trim();
      Integer v = null;
      if (!raw.isEmpty()) {
        require(raw.matches("-?\\d{1,10}"), "Ответ должен быть целым числом");
        try {
          v = Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException("Число вне диапазона");
        }
      }
      parsed.put(eid, v);
    }
    for (var e : parsed.entrySet()) {
      db.run("delete from answers where attempt_id=? and example_id=?", s(t, "id"), e.getKey());
      db.run("insert into answers values(?,?,?)", s(t, "id"), e.getKey(), e.getValue());
    }
    db.run("update attempts set version=version+1 where id=?", s(t, "id"));
    if (submit) finish(a, t, "CHECKED", clock.instant());
    return view(u, a);
  }

  void expire(Map<String, Object> a) {
    if (a.get("current_attempt") == null) return;
    var t = db.one("select * from attempts where id=?", a.get("current_attempt"));
    if (s(t, "status").equals("IN_PROGRESS") && !time(t, "deadline").isAfter(clock.instant()))
      finish(a, t, "EXPIRED", time(t, "deadline"));
  }

  void finish(Map<String, Object> a, Map<String, Object> t, String status, Instant finished) {
    int correct = 0, errors = 0, skipped = 0;
    for (var r :
        db.list(
            "select e.answer,v.value from examples e left join answers v on v.example_id=e.id and"
                + " v.attempt_id=? where e.assignment_id=?",
            s(t, "id"),
            s(a, "id"))) {
      if (r.get("value") == null) skipped++;
      else if (n(r, "answer") == n(r, "value")) correct++;
      else errors++;
    }
    db.run(
        "update attempts set status=?,finished_at=?,correct=?,errors=?,skipped=?,version=version+1"
            + " where id=? and status='IN_PROGRESS'",
        status,
        finished,
        correct,
        errors,
        skipped,
        s(t, "id"));
    invalidate(s(a, "marathon_id"));
    auth.audit(
        null,
        "ATTEMPT_FINISHED",
        s(a, "id"),
        "Попытка "
            + s(t, "id")
            + ", баллы "
            + correct
            + ", ошибки "
            + errors
            + ", пропуски "
            + skipped);
    notifyParent(
        a,
        "finished:" + s(t, "id"),
        "Работа завершена. Баллы: "
            + correct
            + ", ошибки: "
            + errors
            + ", пропуски: "
            + skipped
            + ".");
  }

  Map<String, Object> view(Map<String, Object> u, Map<String, Object> a) {
    var m = marathons.access(u, s(a, "marathon_id"), false);
    var t =
        a.get("current_attempt") == null
            ? null
            : db.one("select * from attempts where id=?", a.get("current_attempt"));
    String status;
    if (a.get("reopened_until") != null && time(a, "reopened_until").isAfter(clock.instant()))
      status = "REOPENED";
    else if (t != null) status = s(t, "status");
    else if (s(m, "status").equals("DRAFT")) status = "DRAFT";
    else if (clock.instant().isBefore(marathons.opens(m, n(a, "day")))) status = "SCHEDULED";
    else if (!clock.instant().isBefore(marathons.opens(m, n(a, "day") + 1))) status = "MISSED";
    else status = "AVAILABLE";
    if (s(m, "status").equals("CANCELLED")) status = "CLOSED";
    boolean staff = !s(u, "role").equals("PARENT"),
        done = t != null && !s(t, "status").equals("IN_PROGRESS");
    List<Map<String, Object>> examples = new ArrayList<>();
    if (staff || t != null)
      for (var e :
          db.list("select * from examples where assignment_id=? order by position", s(a, "id"))) {
        if (t != null) {
          var ans =
              db.maybe(
                  "select value from answers where attempt_id=? and example_id=?",
                  s(t, "id"),
                  s(e, "id"));
          Object value = ans.map(r -> r.get("value")).orElse(null);
          e.put("value", value);
          if (done)
            e.put(
                "result",
                value == null
                    ? "SKIPPED"
                    : ((Number) value).intValue() == n(e, "answer") ? "CORRECT" : "ERROR");
        }
        if (!staff && !done) e.remove("answer");
        examples.add(e);
      }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("assignment", a);
    var child =
        db.one(
            "select id,first_name,last_name,middle_name,category,deleted_at from children where id=?",
            s(a, "child_id"));
    result.put("child", child);
    result.put("attempt", t);
    result.put("status", status);
    result.put("editable",
        staff
            && a.get("current_attempt") == null
            && !Set.of("CANCELLED", "COMPLETED").contains(marathons.effectiveStatus(m))
            && clock.instant().isBefore(marathons.opens(m, n(a, "day") + 1)));
    result.put("examples", examples);
    result.put("marathon", Map.of(
        "id", s(m, "id"),
        "title", s(m, "title"),
        "timezone", s(m, "timezone"),
        "taskDurationMinutes", n(m, "task_duration_minutes")));
    result.put("serverTime", clock.instant());
    result.put("opensAt", marathons.opens(m, n(a, "day")));
    result.put("closesAt", marathons.opens(m, n(a, "day") + 1));
    result.put(
        "exampleCount",
        db.one("select count(*) as total from examples where assignment_id=?", s(a, "id"))
            .get("total"));
    return result;
  }

  @Transactional
  public void reopen(Map<String, Object> u, String aid, Map<String, Object> body) {
    var a = access(u, aid, true);
    requireActiveChild(s(a, "child_id"));
    var m = marathons.access(u, s(a, "marathon_id"), true);
    require(!Set.of("DRAFT", "CANCELLED").contains(s(m, "status")), "Марафон недоступен");
    require(b(body, "confirmed"), "Подтвердите технический сбой");
    String reason = text(body, "reason", 1000);
    require(reason.length() >= 10, "Опишите технический сбой (не менее 10 символов)");
    expire(a);
    require(
        a.get("reopened_until") == null || !time(a, "reopened_until").isAfter(clock.instant()),
        "Задание уже открыто повторно");
    if (a.get("current_attempt") != null) {
      var t = db.one("select * from attempts where id=?", a.get("current_attempt"));
      if (s(t, "status").equals("IN_PROGRESS")) finish(a, t, "INTERRUPTED", clock.instant());
    }
    db.run(
        "insert into reopen_events values(?,?,?,?,?,?)",
        id(),
        aid,
        a.get("current_attempt"),
        s(u, "id"),
        reason,
        clock.instant());
    db.run(
        "update assignments set reopened_until=? where id=?",
        clock.instant().plusSeconds(86400),
        aid);
    invalidate(s(a, "marathon_id"));
    auth.audit(s(u, "id"), "TECHNICAL_REOPEN", aid, reason);
    notifyParent(
        a,
        "reopen:" + id(),
        "Задание повторно открыто после технического сбоя. Начните в течение 24 часов.");
  }

  void invalidate(String mid) {
    db.run("delete from tie_decisions where marathon_id=?", mid);
  }

  void notifyParent(Map<String, Object> a, String key, String text) {
    var c = db.one("select * from children where id=?", s(a, "child_id"));
    if (c.get("deleted_at") != null) return;
    auth.queue(
        s(c, "parent_id"),
        key,
        "Считай. " + s(c, "first_name") + ", день " + n(a, "day") + ". " + text,
        false);
  }

  @Transactional
  public void tick() {
    for (var row :
        db.list(
            "select a.id from assignments a join attempts t on t.id=a.current_attempt where"
                + " t.status='IN_PROGRESS' and t.deadline<=?",
            clock.instant().atOffset(ZoneOffset.UTC))) {
      var a = db.one("select * from assignments where id=? for update", s(row, "id"));
      expire(a);
    }
    for (var m : db.list("select * from marathons where status<>'DRAFT' and status<>'CANCELLED'")) {
      String status = marathons.effectiveStatus(m);
      if (status.equals("ACTIVE")) {
        int day =
            (int)
                    java.time.temporal.ChronoUnit.DAYS.between(
                        (LocalDate) m.get("start_date"), marathons.today(m))
                + 1;
        for (var a :
            db.list("select * from assignments where marathon_id=? and day=?", s(m, "id"), day))
          notifyParent(a, "open:" + s(a, "id"), "Открылось задание дня.");
      }
      if (status.equals("COMPLETED") && !hasPending(s(m, "id"))) {
        for (var c :
            db.list(
                "select c.* from children c join enrollments e on e.child_id=c.id where"
                    + " e.marathon_id=? and c.deleted_at is null",
                s(m, "id")))
          auth.queue(
              s(c, "parent_id"),
              "complete:" + s(m, "id") + ":" + s(c, "id"),
              "Считай: марафон «" + s(m, "title") + "» завершён. Результаты в кабинете.",
              false);
      }
    }
  }

  boolean hasPending(String mid) {
    return db.maybe(
            "select a.id from assignments a join children c on c.id=a.child_id"
                + " left join attempts t on t.id=a.current_attempt where a.marathon_id=?"
                + " and (t.status='IN_PROGRESS' or (c.deleted_at is null and a.reopened_until>?))",
            mid,
            clock.instant().atOffset(ZoneOffset.UTC))
        .isPresent();
  }

  private void requireActiveChild(String cid) {
    require(
        db.one("select deleted_at from children where id=?", cid).get("deleted_at") == null,
        "Карточка ребёнка удалена из кабинета");
  }
}
