package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Marathons {
  private static final ZoneId PROFILE_TIMEZONE = ZoneId.of("Europe/Moscow");
  final Db db;
  final Auth auth;
  final Clock clock;
  final XlsxSyllabusParser syllabusParser;

  Marathons(Db db, Auth auth, Clock clock, XlsxSyllabusParser syllabusParser) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
    this.syllabusParser = syllabusParser;
  }

  public Map<String, Object> access(Map<String, Object> u, String mid, boolean write) {
    return access(u, mid, write, false);
  }

  Map<String, Object> lock(Map<String, Object> u, String mid, boolean write) {
    return access(u, mid, write, true);
  }

  private Map<String, Object> access(
      Map<String, Object> u, String mid, boolean write, boolean lock) {
    var m = db.one("select * from marathons where id=?" + (lock ? " for update" : ""), mid);
    if (s(u, "role").equals("ADMIN")) return m;
    if (s(u, "role").equals("TEACHER")
        && db.maybe(
                "select user_id from marathon_teachers where marathon_id=? and user_id=?",
                mid,
                s(u, "id"))
            .isPresent()) return m;
    if (!write
        && s(u, "role").equals("PARENT")
        && db.maybe(
                "select c.id from children c join enrollments e on e.child_id=c.id where"
                    + " e.marathon_id=? and c.parent_id=? and c.deleted_at is null",
                mid,
                s(u, "id"))
            .isPresent()) return m;
    forbidden();
    return m;
  }

  public Map<String, Object> ownChild(Map<String, Object> u, String cid) {
    var c = db.one("select * from children where id=? and deleted_at is null", cid);
    if (!s(c, "parent_id").equals(s(u, "id"))) forbidden();
    return c;
  }

  public LocalDate today(Map<String, Object> m) {
    return LocalDate.now(clock.withZone(ZoneId.of(s(m, "timezone"))));
  }

  public Instant opens(Map<String, Object> m, int day) {
    return ((LocalDate) m.get("start_date"))
        .plusDays(day - 1)
        .atStartOfDay(ZoneId.of(s(m, "timezone")))
        .toInstant();
  }

  public String effectiveStatus(Map<String, Object> m) {
    String status = s(m, "status");
    if (Set.of("DRAFT", "CANCELLED").contains(status)) return status;
    LocalDate today = today(m), start = (LocalDate) m.get("start_date");
    return today.isBefore(start)
        ? "SCHEDULED"
        : today.isBefore(start.plusDays(n(m, "days"))) ? "ACTIVE" : "COMPLETED";
  }

  @Transactional
  public Map<String, Object> child(Map<String, Object> u, Map<String, Object> body) {
    auth.parent(u);
    String cid = id();
    LocalDate today = LocalDate.now(clock.withZone(PROFILE_TIMEZONE));
    int minYear = today.getYear() - 25;
    LocalDate birthDate = body.containsKey("birthDate") ? readBirthDate(body, today, minYear) : null;
    int birthYear =
        birthDate == null ? number(body, "birthYear", minYear, today.getYear()) : birthDate.getYear();
    db.run(
        "insert into children"
            + "(id,parent_id,last_name,first_name,middle_name,birth_year,birth_date,category,gender)"
            + " values(?,?,?,?,?,?,?,?,?)",
        cid,
        s(u, "id"),
        text(body, "lastName", 80),
        text(body, "firstName", 80),
        optional(body, "middleName", 80),
        birthYear,
        birthDate,
        choice(body, "category", "BABY", "SCHOOL"),
        choice(body, "gender", "MALE", "FEMALE"));
    auth.audit(s(u, "id"), "CHILD_CREATED", cid, "Добавлен профиль ребёнка");
    return db.one("select * from children where id=?", cid);
  }

  @Transactional
  public void updateChildBirthDate(Map<String, Object> u, String cid, Map<String, Object> body) {
    auth.parent(u);
    var child = ownChild(u, cid);
    LocalDate today = LocalDate.now(clock.withZone(PROFILE_TIMEZONE));
    int minYear = Math.min(today.getYear() - 25, n(child, "birth_year"));
    LocalDate birthDate = readBirthDate(body, today, minYear);
    require(
        db.run(
                "update children set birth_date=?,birth_year=? where id=? and deleted_at is null",
                birthDate,
                birthDate.getYear(),
                cid)
            > 0,
        "Карточка ребёнка удалена");
    auth.audit(s(u, "id"), "CHILD_BIRTH_DATE_UPDATED", cid, "Указана дата рождения ребёнка");
  }

  private LocalDate readBirthDate(Map<String, Object> body, LocalDate today, int minYear) {
    String value = s(body, "birthDate").trim();
    require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "Укажите полную дату рождения");
    LocalDate birthDate;
    try {
      birthDate = LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте дату рождения", e);
    }
    require(!birthDate.isAfter(today), "Дата рождения не может быть в будущем");
    require(birthDate.getYear() >= minYear, "Год рождения должен быть не раньше " + minYear);
    return birthDate;
  }

  @Transactional
  public void updateChildGender(Map<String, Object> u, String cid, Map<String, Object> body) {
    auth.parent(u);
    ownChild(u, cid);
    String gender = choice(body, "gender", "MALE", "FEMALE");
    db.run("update children set gender=? where id=?", gender, cid);
    auth.audit(s(u, "id"), "CHILD_GENDER_UPDATED", cid, "Указан пол ребёнка");
  }

  @Transactional
  public void deleteChild(Map<String, Object> u, String cid) {
    auth.parent(u);
    var child = db.one("select parent_id from children where id=?", cid);
    if (!s(child, "parent_id").equals(s(u, "id"))) forbidden();
    if (db.run("update children set deleted_at=? where id=? and deleted_at is null",
            clock.instant(), cid) > 0) {
      auth.audit(s(u, "id"), "CHILD_DELETED", cid, "Карточка ребёнка удалена из кабинета");
    }
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> u, Map<String, Object> body) {
    auth.staff(u);
    String mid = id(), timezone = optional(body, "timezone", 80);
    if (timezone.isEmpty()) timezone = "Europe/Moscow";
    ZoneId.of(timezone);
    LocalDate start = readStartDate(body);
    int days = body.containsKey("days") ? number(body, "days", 1, 31) : 6;
    int preschoolCount = body.containsKey("preschoolExampleCount")
        ? number(body, "preschoolExampleCount", 1, 200) : 60;
    int schoolCount = body.containsKey("schoolExampleCount")
        ? number(body, "schoolExampleCount", 1, 200) : 100;
    int duration = body.containsKey("taskDurationMinutes")
        ? number(body, "taskDurationMinutes", 1, 240) : 30;
    String code =
        UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    db.run(
        "insert into marathons(id,title,description,start_date,days,timezone,status,published,"
            + "join_code,created_by,preschool_example_count,school_example_count,task_duration_minutes)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        mid,
        text(body, "title", 150),
        optional(body, "description", 2000),
        start,
        days,
        timezone,
        "DRAFT",
        b(body, "published"),
        code,
        s(u, "id"),
        preschoolCount,
        schoolCount,
        duration);
    db.run("insert into marathon_teachers values(?,?)", mid, s(u, "id"));
    auth.audit(s(u, "id"), "MARATHON_CREATED", mid, "Создан марафон");
    return db.one("select * from marathons where id=?", mid);
  }

  private LocalDate readStartDate(Map<String, Object> body) {
    String value = s(body, "startDate").trim();
    require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "Укажите дату начала марафона");
    try {
      LocalDate date = LocalDate.parse(value);
      require(date.getYear() > 0, "Проверьте дату начала марафона");
      return date;
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте дату начала марафона", e);
    }
  }

  @Transactional
  public void update(Map<String, Object> u, String mid, Map<String, Object> body) {
    var m = lock(u, mid, true);
    LocalDate previousStart = (LocalDate) m.get("start_date");
    LocalDate start = body.containsKey("startDate") ? readStartDate(body) : previousStart;
    String status =
        body.containsKey("status")
            ? choice(body, "status", "DRAFT", "SCHEDULED", "CANCELLED")
            : s(m, "status");
    if (status.equals("DRAFT"))
      require(
          db.maybe(
                  "select t.id from attempts t join assignments a on a.id=t.assignment_id where"
                      + " a.marathon_id=?",
                  mid)
              .isEmpty(),
          "Начавшийся марафон нельзя вернуть в черновик");
    int preschoolCount = body.containsKey("preschoolExampleCount")
        ? number(body, "preschoolExampleCount", 1, 200) : n(m, "preschool_example_count");
    int schoolCount = body.containsKey("schoolExampleCount")
        ? number(body, "schoolExampleCount", 1, 200) : n(m, "school_example_count");
    int duration = body.containsKey("taskDurationMinutes")
        ? number(body, "taskDurationMinutes", 1, 240) : n(m, "task_duration_minutes");
    db.run(
        "update marathons set title=?,description=?,published=?,status=?,start_date=?,"
            + "preschool_example_count=?,school_example_count=?,task_duration_minutes=? where id=?",
        body.containsKey("title") ? text(body, "title", 150) : m.get("title"),
        body.containsKey("description")
            ? optional(body, "description", 2000)
            : m.get("description"),
        body.containsKey("published") ? b(body, "published") : b(m, "published"),
        status,
        start,
        preschoolCount,
        schoolCount,
        duration,
        mid);
    auth.audit(
        s(u, "id"), "MARATHON_UPDATED", mid,
        start.equals(previousStart) ? "Настройки и статус" : "Дата начала: " + previousStart + " → " + start);
  }

  @Transactional
  public Map<String, Object> uploadSyllabus(
      Map<String, Object> u, String mid, MultipartFile file) throws IOException {
    var marathon = lock(u, mid, true);
    require(file != null && file.getSize() > 0 && file.getSize() <= 5 * 1024 * 1024,
        "Файл силлабуса должен быть не больше 5 МБ");
    String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
    require(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"),
        "Загрузите силлабус в формате .xlsx");
    var parsed = syllabusParser.parse(file.getBytes());
    require(parsed.preschoolCount() > 0 && parsed.schoolCount() > 0,
        "Силлабус должен содержать правила и для дошкольников, и для школьников");
    db.run("delete from marathon_syllabus_rules where marathon_id=?", mid);
    for (var rule : parsed.rules())
      db.run(
          "insert into marathon_syllabus_rules"
              + "(marathon_id,age_band,category,competition_category,position,digits,terms,operation,example_count)"
              + " values(?,?,?,?,?,?,?,?,?)",
          mid, rule.ageBand(), rule.category(), rule.competitionCategory(), rule.position(),
          rule.digits(), rule.terms(), rule.operation(), rule.count());
    String safeName = filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    db.run(
        "update marathons set syllabus_name=?,preschool_example_count=?,school_example_count=? where id=?",
        safeName, parsed.preschoolCount(), parsed.schoolCount(), mid);
    auth.audit(s(u, "id"), "SYLLABUS_UPLOADED", mid,
        safeName + ", дошкольники: " + parsed.preschoolCount()
            + ", школьники: " + parsed.schoolCount());
    return Map.of(
        "name", safeName,
        "preschoolExampleCount", parsed.preschoolCount(),
        "schoolExampleCount", parsed.schoolCount(),
        "ruleCount", parsed.rules().size(),
        "previousName", Objects.toString(marathon.get("syllabus_name"), ""));
  }

  @Transactional
  public void teacher(Map<String, Object> u, String mid, String uid) {
    teachers(u, mid, List.of(uid));
  }

  @Transactional
  public void teachers(Map<String, Object> u, String mid, List<String> userIds) {
    auth.admin(u);
    access(u, mid, true);
    require(!userIds.isEmpty() && userIds.size() <= 100, "Выберите от 1 до 100 педагогов");
    var unique = userIds.stream().distinct().toList();
    for (String uid : unique) {
      var teacher = db.one("select * from users where id=?", uid);
      require(b(teacher, "enabled"), "Аккаунт педагога отключён");
      require(Set.of("TEACHER", "ADMIN").contains(s(teacher, "role")), "Нужна роль педагога");
    }
    int added = 0;
    for (String uid : unique)
      if (db.maybe("select * from marathon_teachers where marathon_id=? and user_id=?", mid, uid)
          .isEmpty()) {
        db.run("insert into marathon_teachers values(?,?)", mid, uid);
        added++;
      }
    auth.audit(s(u, "id"), "TEACHERS_ASSIGNED", mid,
        "Выбрано: " + unique.size() + ", добавлено: " + added);
  }

  @Transactional
  public void join(Map<String, Object> u, Map<String, Object> body) {
    auth.parent(u);
    var c = ownChild(u, text(body, "childId", 36));
    String code = text(body, "code", 16).toUpperCase(Locale.ROOT);
    var m = db.one("select * from marathons where join_code=?", code);
    require(
        !Set.of("CANCELLED", "COMPLETED").contains(effectiveStatus(m)), "Приём участников закрыт");
    if (db.maybe(
            "select * from enrollments where marathon_id=? and child_id=?", s(m, "id"), s(c, "id"))
        .isPresent()) return;
    db.run("insert into enrollments(marathon_id,child_id) values(?,?)", s(m, "id"), s(c, "id"));
    auth.audit(s(u, "id"), "ENROLLED", s(c, "id"), s(m, "id"));
    auth.queue(
        s(u, "id"),
        "enrolled:" + s(m, "id") + ":" + s(c, "id"),
        "Считай: ребёнок добавлен в марафон «" + s(m, "title") + "».",
        false);
  }

  public Map<String, Object> dashboard(Map<String, Object> u) {
    List<Map<String, Object>> ms;
    if (s(u, "role").equals("ADMIN"))
      ms = db.list("select * from marathons order by start_date desc");
    else if (s(u, "role").equals("TEACHER"))
      ms =
          db.list(
              "select m.* from marathons m join marathon_teachers t on t.marathon_id=m.id where"
                  + " t.user_id=? order by start_date desc",
              s(u, "id"));
    else
      ms =
          db.list(
              "select distinct m.* from marathons m join enrollments e on e.marathon_id=m.id join"
                  + " children c on c.id=e.child_id where c.parent_id=? and c.deleted_at is null"
                  + " order by start_date desc",
              s(u, "id"));
    ms.forEach(
        m -> {
          m.put("effective_status", effectiveStatus(m));
          if (s(u, "role").equals("PARENT")) m.remove("join_code");
        });
    return Map.of(
        "children",
        s(u, "role").equals("PARENT")
            ? db.list(
                "select * from children where parent_id=? and deleted_at is null order by first_name",
                s(u, "id"))
            : List.of(),
        "marathons",
        ms);
  }

  @Transactional
  public void delete(Map<String, Object> u, String mid) {
    auth.admin(u);
    var marathon = db.maybe("select * from marathons where id=? for update", mid);
    if (marathon.isEmpty()) return;

    var assignmentIds = db.list("select id from assignments where marathon_id=?", mid).stream()
        .map(row -> s(row, "id"))
        .toList();
    for (String assignmentId : assignmentIds) {
      db.run("update assignments set current_attempt=null where id=?", assignmentId);
      db.run(
          "delete from answers where attempt_id in"
              + " (select id from attempts where assignment_id=?)",
          assignmentId);
      db.run(
          "delete from answers where example_id in"
              + " (select id from examples where assignment_id=?)",
          assignmentId);
      db.run("delete from reopen_events where assignment_id=?", assignmentId);
      db.run("delete from attempts where assignment_id=?", assignmentId);
      db.run("delete from examples where assignment_id=?", assignmentId);
    }
    db.run("delete from assignments where marathon_id=?", mid);
    db.run("delete from tie_decisions where marathon_id=?", mid);
    db.run("delete from marathon_syllabus_rules where marathon_id=?", mid);
    db.run("delete from enrollments where marathon_id=?", mid);
    db.run("delete from marathon_teachers where marathon_id=?", mid);
    db.run("delete from marathons where id=?", mid);
    auth.audit(
        s(u, "id"),
        "MARATHON_DELETED",
        mid,
        "Удалён марафон «" + s(marathon.orElseThrow(), "title") + "»");
  }

  @Transactional
  public void updateCompetitionCategory(
      Map<String, Object> u, String mid, String cid, Map<String, Object> body) {
    access(u, mid, true);
    db.one("select id from marathons where id=? for update", mid);
    var participant = db.one(
        "select e.competition_category,c.category from enrollments e join children c on c.id=e.child_id"
            + " where e.marathon_id=? and e.child_id=? and c.deleted_at is null for update",
        mid, cid);
    require(body.containsKey("competitionCategory"), "Укажите категорию соревнования");
    String competition = optional(body, "competitionCategory", 24);
    String category = s(participant, "category"), previous = s(participant, "competition_category");
    CompetitionCategory.validate(competition, category);
    if (competition.equals(previous)) return;
    db.run("update enrollments set competition_category=? where marathon_id=? and child_id=?",
        competition, mid, cid);
    auth.audit(s(u, "id"), "COMPETITION_CATEGORY_UPDATED", cid, mid + " " + competition);
  }

  public Map<String, Object> detail(Map<String, Object> u, String mid) {
    var m = access(u, mid, false);
    boolean parent = s(u, "role").equals("PARENT");
    List<Map<String, Object>> children =
        parent
            ? db.list(
                "select c.*,e.competition_category from children c join enrollments e on e.child_id=c.id where"
                    + " e.marathon_id=? and c.parent_id=? and c.deleted_at is null order by last_name",
                mid,
                s(u, "id"))
            : db.list(
                "select c.*,e.competition_category from children c join enrollments e on e.child_id=c.id where"
                    + " e.marathon_id=? order by last_name",
                mid);
    var assignments = new ArrayList<Map<String, Object>>();
    LocalDate today = today(m);
    LocalDate startDate = (LocalDate) m.get("start_date");
    for (var c : children) {
      if (!parent) {
        var birthDate = (LocalDate) c.remove("birth_date");
        c.put("age", birthDate == null ? null : Period.between(birthDate, today).getYears());
        c.put("age_at_start",
            birthDate == null ? null : Period.between(birthDate, startDate).getYears());
      }
      for (var a :
          db.list(
              "select a.*,t.status as"
                  + " attempt_status,t.correct,t.errors,t.skipped,t.started_at,t.finished_at,t.deadline"
                  + " from assignments a left join attempts t on t.id=a.current_attempt where"
                  + " a.marathon_id=? and a.child_id=? order by a.day",
              mid,
              s(c, "id"))) {
        a.put("opens_at", opens(m, n(a, "day")));
        a.put("closes_at", opens(m, n(a, "day") + 1));
        assignments.add(a);
      }
    }
    String status = effectiveStatus(m);
    m.put("effective_status", status);
    var assignableDays = new ArrayList<Integer>();
    if (!parent && !Set.of("CANCELLED", "COMPLETED").contains(status)) {
      Instant now = clock.instant();
      for (int day = 1; day <= n(m, "days"); day++)
        if (now.isBefore(opens(m, day + 1))) assignableDays.add(day);
    }
    if (parent) m.remove("join_code");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("marathon", m);
    result.put("children", children);
    result.put("assignments", assignments);
    result.put("assignableDays", assignableDays);
    result.put("teachers", parent ? List.of() : db.list(
        "select u.id,u.full_name from users u join marathon_teachers t on t.user_id=u.id"
            + " where t.marathon_id=?", mid));
    result.put("syllabus", parent ? List.of() : db.list(
        "select age_band,category,competition_category,position,digits,terms,operation,"
            + "example_count as count from marathon_syllabus_rules where marathon_id=?"
            + " order by age_band,category,competition_category,position", mid));
    return result;
  }
}
