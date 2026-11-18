package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import java.io.*;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Rankings {
  final Db db;
  final Auth auth;
  final Marathons marathons;
  final Assignments assignments;
  final Clock clock;

  Rankings(Db db, Auth auth, Marathons marathons, Assignments assignments, Clock clock) {
    this.db = db;
    this.auth = auth;
    this.marathons = marathons;
    this.assignments = assignments;
    this.clock = clock;
  }

  @Transactional
  public List<Map<String, Object>> get(Map<String, Object> u, String mid) {
    var m = marathons.access(u, mid, false);
    boolean parent = s(u, "role").equals("PARENT");
    if (parent && !b(m, "published")) forbidden();
    var rows = calculate(m);
    if (!parent) return rows;

    var categories = new HashSet<String>();
    for (var row : rows)
      if (s(u, "id").equals(s(row, "parent_id")) && row.get("deleted_at") == null)
        categories.add(s(row, "category"));
    rows = rows.stream().filter(row -> categories.contains(s(row, "category"))).toList();
    for (var r : rows) {
      if (r.remove("deleted_at") != null || !b(r, "ranking_consent")) {
        r.put("name", "Участник без публикации ФИО");
        r.remove("child_id");
      }
      r.remove("ranking_consent");
      r.remove("parent_id");
      r.remove("birth_year");
      r.remove("competition_category");
    }
    return rows;
  }

  private List<Map<String, Object>> rankByCategory(
      List<Map<String, Object>> rows, Map<String, Map<String, Object>> decisions) {
    List<Map<String, Object>> result = new ArrayList<>();
    var groups = new TreeMap<String, List<Map<String, Object>>>(Comparator.reverseOrder());
    for (var row : rows)
      groups.computeIfAbsent(s(row, "category"), key -> new ArrayList<>()).add(row);
    for (var categoryRows : groups.values()) {
      categoryRows.sort(
          Comparator.<Map<String, Object>>comparingInt(row -> n(row, "score"))
              .reversed()
              .thenComparing(row -> s(row, "name"))
              .thenComparing(row -> s(row, "child_id")));
      for (int start = 0; start < categoryRows.size(); ) {
        int end = start + 1;
        int score = n(categoryRows.get(start), "score");
        while (end < categoryRows.size() && n(categoryRows.get(end), "score") == score) end++;
        var group = categoryRows.subList(start, end);
        boolean tied = group.size() > 1;
        boolean resolved =
            tied
                && group.stream()
                    .allMatch(
                        row -> {
                          var decision = decisions.get(s(row, "child_id"));
                          return decision != null && n(decision, "score") == score;
                        });
        // A saved rank can come from the former combined ranking. Use its relative order.
        if (resolved)
          group = group.stream()
              .sorted(Comparator.comparingInt(row -> n(decisions.get(s(row, "child_id")), "rank")))
              .toList();
        for (int i = 0; i < group.size(); i++) {
          var row = group.get(i);
          row.put("rank", start + (resolved ? i : 0) + 1);
          row.put("tie", tied && !resolved);
          row.put("decision", resolved ? s(decisions.get(s(row, "child_id")), "reason") : "");
          result.add(row);
        }
        start = end;
      }
    }
    return result;
  }

  List<Map<String, Object>> calculate(Map<String, Object> m) {
    String mid = s(m, "id");
    for (var a : db.list("select * from assignments where marathon_id=?", mid)) {
      var locked = db.one("select * from assignments where id=? for update", s(a, "id"));
      assignments.expire(locked);
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (var c :
        db.list(
            "select c.*,e.competition_category,u.ranking_consent from children c"
                + " join enrollments e on e.child_id=c.id"
                + " join users u on u.id=c.parent_id where e.marathon_id=?",
            mid)) {
      int score = 0, completed = 0, missed = 0, errors = 0, skipped = 0;
      List<Integer> days = new ArrayList<>();
      for (int day = 1; day <= n(m, "days"); day++) {
        var attempt =
            db.maybe(
                "select t.* from assignments a left join attempts t on t.id=a.current_attempt where"
                    + " a.marathon_id=? and a.child_id=? and a.day=?",
                mid,
                s(c, "id"),
                day);
        int points = 0;
        if (attempt.isPresent()
            && attempt.get().get("status") != null
            && !s(attempt.get(), "status").equals("IN_PROGRESS")) {
          var t = attempt.get();
          points = n(t, "correct");
          completed++;
          errors += n(t, "errors");
          skipped += n(t, "skipped");
        } else if (!clock.instant().isBefore(marathons.opens(m, day + 1))) missed++;
        days.add(points);
        score += points;
      }
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("child_id", s(c, "id"));
      r.put("parent_id", s(c, "parent_id"));
      r.put(
          "name",
          (s(c, "last_name") + " " + s(c, "first_name") + " " + s(c, "middle_name")).trim());
      r.put("category", s(c, "category"));
      r.put("competition_category", s(c, "competition_category"));
      r.put("birth_year", n(c, "birth_year"));
      r.put("ranking_consent", b(c, "ranking_consent"));
      r.put("deleted_at", c.get("deleted_at"));
      r.put("days", days);
      r.put("score", score);
      r.put("completed_days", completed);
      r.put("missed_days", missed);
      r.put("errors", errors);
      r.put("skipped", skipped);
      rows.add(r);
    }
    var decisions = new HashMap<String, Map<String, Object>>();
    for (var decision : db.list(
        "select * from tie_decisions where marathon_id=? and applies_to_category=true", mid))
      decisions.put(s(decision, "child_id"), decision);
    return rankByCategory(rows, decisions);
  }

  @Transactional
  public void resolve(Map<String, Object> u, String mid, Map<String, Object> body) {
    var m = marathons.lock(u, mid, true);
    require(
        marathons.effectiveStatus(m).equals("COMPLETED"),
        "Очная встреча фиксируется после окончания марафона");
    require(!assignments.hasPending(mid), "Есть незавершённые или повторно открытые задания");
    var children = ids(body, "childIds");
    require(children.size() >= 2, "Выберите всю группу с равным результатом");
    String reason = text(body, "reason", 1000);
    require(reason.length() >= 10, "Укажите протокол очной встречи");
    var rows = calculate(m);
    var first =
        rows.stream()
            .filter(r -> s(r, "child_id").equals(children.getFirst()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
    int score = n(first, "score");
    String category = s(first, "category");
    var categoryRows = rows.stream().filter(r -> category.equals(s(r, "category"))).toList();
    var group = categoryRows.stream().filter(r -> n(r, "score") == score).toList();
    require(
        new HashSet<>(group.stream().map(r -> s(r, "child_id")).toList())
            .equals(new HashSet<>(children)),
        "Укажите всех участников с одинаковыми баллами в одной категории");
    int rank = (int) categoryRows.stream().filter(r -> n(r, "score") > score).count() + 1;
    for (String cid : children) {
      db.run("delete from tie_decisions where marathon_id=? and child_id=?", mid, cid);
      db.run(
          "insert into tie_decisions(marathon_id,child_id,rank,score,reason,actor_id,at)"
              + " values(?,?,?,?,?,?,?)",
          mid,
          cid,
          rank++,
          score,
          reason,
          s(u, "id"),
          clock.instant());
    }
    auth.audit(s(u, "id"), "TIE_RESOLVED", mid, reason);
  }

  @Transactional
  public void notifyTies() {
    for (var m : db.list("select * from marathons where status<>'DRAFT' and status<>'CANCELLED'")) {
      String mid = s(m, "id");
      if (!marathons.effectiveStatus(m).equals("COMPLETED") || assignments.hasPending(mid)) continue;
      for (var r : calculate(m)) {
        if (!b(r, "tie") || r.get("deleted_at") != null) continue;
        String key = "tie:" + mid + ":" + s(r, "child_id") + ":" + n(r, "score");
        if (db.maybe("select id from sms_outbox where event_key like ? limit 1", key + ":%")
            .isPresent()) continue;
        auth.queue(
            s(r, "parent_id"),
            key,
            "Считай: равный результат в марафоне «"
                + s(m, "title")
                + "». Требуется очная встреча. Свяжитесь с педагогом.",
            false);
      }
    }
  }

  static String safeCell(Object value) {
    String s = Objects.toString(value, "");
    return s.stripLeading().matches("^[=+@\\-\\t\\r].*") ? "'" + s : s;
  }

  static String csv(List<List<Object>> rows) throws IOException {
    StringWriter out = new StringWriter();
    out.write('\uFEFF');
    try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180)) {
      for (var row : rows) printer.printRecord(row.stream().map(Rankings::safeCell).toList());
    }
    return out.toString();
  }

  @Transactional
  public String export(Map<String, Object> u, String mid) throws IOException {
    var m = marathons.access(u, mid, true);
    var rows = new ArrayList<List<Object>>();
    rows.add(
        List.of(
            "ФИО",
            "Год рождения",
            "Категория",
            "День",
            "Баллы",
            "Ошибки",
            "Пропуски примеров",
            "Начало",
            "Завершение",
            "Секунды",
            "Статус",
            "Итого",
            "Место",
            "Пропущено дней",
            "Очная встреча",
            "Категория соревнования"));
    for (var r : calculate(m))
      for (int day = 1; day <= n(m, "days"); day++) {
        var t =
            db.maybe(
                    "select t.* from assignments a left join attempts t on t.id=a.current_attempt"
                        + " where a.marathon_id=? and a.child_id=? and a.day=?",
                    mid,
                    s(r, "child_id"),
                    day)
                .orElse(Map.of());
        boolean done = t.get("finished_at") != null;
        String status =
            t.get("status") == null
                ? (clock.instant().isBefore(marathons.opens(m, day + 1)) ? "SCHEDULED" : "MISSED")
                : s(t, "status");
        rows.add(
            Arrays.asList(
                r.get("name"),
                r.get("birth_year"),
                r.get("category"),
                day,
                done ? t.get("correct") : 0,
                done ? t.get("errors") : 0,
                done ? t.get("skipped") : 0,
                t.get("started_at"),
                t.get("finished_at"),
                done
                    ? Duration.between(time(t, "started_at"), time(t, "finished_at")).toSeconds()
                    : "",
                status,
                r.get("score"),
                r.get("rank"),
                r.get("missed_days"),
                r.get("tie"),
                CompetitionCategory.label(s(r, "competition_category"))));
      }
    return csv(rows);
  }

  @Transactional
  public String childExport(Map<String, Object> u, String cid, String mid) throws IOException {
    marathons.access(u, mid, true);
    require(
        db.maybe("select * from enrollments where child_id=? and marathon_id=?", cid, mid)
            .isPresent(),
        "Ребёнок не участвует");
    var rows = new ArrayList<List<Object>>();
    rows.add(
        List.of(
            "День",
            "Пример",
            "Эталон",
            "Ответ",
            "Результат",
            "Балл",
            "Начало",
            "Завершение",
            "Статус"));
    for (var a :
        db.list(
            "select * from assignments where child_id=? and marathon_id=? order by day",
            cid,
            mid)) {
      var view = assignments.get(u, s(a, "id"));
      @SuppressWarnings("unchecked")
      var es = (List<Map<String, Object>>) view.get("examples");
      @SuppressWarnings("unchecked")
      var t = (Map<String, Object>) view.get("attempt");
      for (var e : es)
        rows.add(
            Arrays.asList(
                a.get("day"),
                e.get("expression"),
                e.get("answer"),
                e.get("value"),
                e.get("result"),
                "CORRECT".equals(e.get("result")) ? 1 : 0,
                t == null ? "" : t.get("started_at"),
                t == null ? "" : t.get("finished_at"),
                view.get("status")));
    }
    return csv(rows);
  }
}
