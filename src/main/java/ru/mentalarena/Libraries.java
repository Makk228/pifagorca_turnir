package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class Libraries {
  final Db db;
  final Auth auth;
  final Clock clock;
  final XlsxLibraryParser xlsx;
  final SecureRandom random = new SecureRandom();

  Libraries(Db db, Auth auth, Clock clock, XlsxLibraryParser xlsx) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
    this.xlsx = xlsx;
  }

  record Rule(int digits, int terms, String operation, int count) {}

  public List<Map<String, Object>> list(Map<String, Object> u) {
    auth.staff(u);
    return s(u, "role").equals("ADMIN")
        ? db.list(
            "select l.*,count(e.id) as example_count from libraries l left join library_examples e"
                + " on e.library_id=l.id group by l.id order by l.created_at desc")
        : db.list(
            "select l.*,count(e.id) as example_count from libraries l left join library_examples e"
                + " on e.library_id=l.id where l.owner_id=? group by l.id order by l.created_at"
                + " desc",
            s(u, "id"));
  }

  @Transactional
  public Map<String, Object> upload(
      Map<String, Object> u, String name, String competitionCategory, MultipartFile file)
      throws IOException {
    auth.staff(u);
    CompetitionCategory.validate(competitionCategory, "");
    require(
        name != null && !name.isBlank() && name.length() <= 120,
        "Название библиотеки: 1–120 символов");
    require(file.getSize() > 0 && file.getSize() <= 10 * 1024 * 1024,
        "Файл должен быть не больше 10 МБ");
    String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
    if (filename.endsWith(".xlsx"))
      return uploadXlsx(u, name, competitionCategory, file.getBytes());
    require(filename.endsWith(".csv"), "Загрузите файл .csv или .xlsx");
    return uploadCsv(u, name, competitionCategory, file.getBytes());
  }

  private Map<String, Object> uploadCsv(
      Map<String, Object> u, String name, String competitionCategory, byte[] bytes)
      throws IOException {
    String content = new String(bytes, StandardCharsets.UTF_8);
    if (content.startsWith("\uFEFF")) content = content.substring(1);
    require(!content.contains("\uFFFD"), "Сохраните CSV в кодировке UTF-8");
    var format =
        CSVFormat.RFC4180
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .get();
    String lid = id();
    int count = 0;
    try (var parser = CSVParser.parse(content, format)) {
      require(
          parser
              .getHeaderNames()
              .equals(List.of("category", "topic", "level", "expression", "answer")),
          "Заголовок CSV: category,topic,level,expression,answer");
      db.run(
          "insert into libraries(id,owner_id,name,created_at,competition_category) values(?,?,?,?,?)",
          lid, s(u, "id"), name.trim(), clock.instant(), competitionCategory);
      for (var r : parser) {
        require(r.isConsistent(), "Неверное количество колонок в строке " + r.getRecordNumber());
        var m = new HashMap<String, Object>(r.toMap());
        String cat = choice(m, "category", "BABY", "SCHOOL"),
            level = choice(m, "level", "BASIC", "MEDIUM", "HARD"),
            topic = text(m, "topic", 80),
            expression = text(m, "expression", 200);
        int answer = number(m, "answer", Integer.MIN_VALUE, Integer.MAX_VALUE);
        CompetitionCategory.validate(competitionCategory, cat);
        require(++count <= 10000, "Не более 10 000 примеров в файле");
        db.run(
            "insert into library_examples(id,library_id,category,topic,level,expression,answer)"
                + " values(?,?,?,?,?,?,?)",
            id(),
            lid,
            cat,
            topic,
            level,
            expression,
            answer);
      }
    } catch (UncheckedIOException e) {
      throw new IllegalArgumentException("Некорректный CSV", e);
    }
    require(count > 0, "В файле нет примеров");
    auth.audit(s(u, "id"), "LIBRARY_IMPORTED", lid, "Примеров: " + count);
    return Map.of("id", lid, "count", count);
  }

  private Map<String, Object> uploadXlsx(
      Map<String, Object> u, String name, String competitionCategory, byte[] bytes) {
    require(!competitionCategory.isBlank(), "Для Excel выберите категорию соревнования");
    var parsed = xlsx.parse(bytes);
    require(parsed.size() <= 20000, "В Excel должно быть не больше 20 000 уникальных примеров");
    String lid = id();
    db.run(
        "insert into libraries(id,owner_id,name,created_at,competition_category) values(?,?,?,?,?)",
        lid, s(u, "id"), name.trim(), clock.instant(), competitionCategory);
    List<String> categories = competitionCategory.equals("LAWS_PLUS_10")
        ? List.of("BABY") : List.of("BABY", "SCHOOL");
    int count = 0;
    for (String category : categories) {
      CompetitionCategory.validate(competitionCategory, category);
      for (var example : parsed) {
        db.run(
            "insert into library_examples"
                + "(id,library_id,category,topic,level,expression,answer,digits,terms,operation)"
                + " values(?,?,?,?,?,?,?,?,?,?)",
            id(), lid, category, example.digits() + "D" + example.terms() + "R", "BASIC",
            example.expression(), example.answer(), example.digits(), example.terms(),
            example.operation());
        count++;
      }
    }
    auth.audit(s(u, "id"), "LIBRARY_IMPORTED", lid,
        "Excel, уникальных примеров: " + parsed.size());
    return Map.of("id", lid, "count", count, "uniqueCount", parsed.size());
  }

  @Transactional
  public void delete(Map<String, Object> u, String lid) {
    auth.staff(u);
    var library = db.maybe("select * from libraries where id=? for update", lid);
    if (library.isEmpty()) return;
    requireLibraryAccess(u, library.get());
    db.run("delete from library_examples where library_id=?", lid);
    db.run("delete from libraries where id=?", lid);
    auth.audit(s(u, "id"), "LIBRARY_DELETED", lid, s(library.get(), "name"));
  }

  private void requireLibraryAccess(Map<String, Object> u, Map<String, Object> library) {
    if (!s(u, "role").equals("ADMIN") && !s(library, "owner_id").equals(s(u, "id"))) forbidden();
  }

  public Map<String, String> requireOnePerCompetitionCategory(
      Map<String, Object> u, List<String> ids, Set<String> competitionCategories) {
    auth.staff(u);
    var selected = new LinkedHashMap<String, List<String>>();
    for (String id : ids) {
      var library = db.one("select * from libraries where id=?", id);
      requireLibraryAccess(u, library);
      String category = s(library, "competition_category");
      require(
          competitionCategories.contains(category),
          "Файл «"
              + s(library, "name")
              + "» относится к категории «"
              + (category.isBlank() ? "не назначена" : CompetitionCategory.label(category))
              + "» и не подходит выбранным участникам");
      selected.computeIfAbsent(category, ignored -> new ArrayList<>()).add(id);
    }
    var result = new LinkedHashMap<String, String>();
    for (String category : competitionCategories) {
      var matching = selected.getOrDefault(category, List.of());
      require(
          matching.size() == 1,
          "Для категории «"
              + CompetitionCategory.label(category)
              + "» выберите ровно один файл библиотеки");
      result.put(category, matching.get(0));
    }
    return result;
  }

  public List<Map<String, Object>> select(
      Map<String, Object> u,
      List<String> ids,
      String category,
      String competitionCategory,
      String level,
      String topic,
      int count) {
    auth.staff(u);
    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
    for (String lid : ids) {
      var library = db.one("select * from libraries where id=?", lid);
      requireLibraryAccess(u, library);
      if (!competitionCategory.equals(s(library, "competition_category"))) continue;
      for (var e :
          db.list(
              "select * from library_examples where library_id=? and category=?", lid, category)) {
        if (!level.isBlank() && !s(e, "level").equals(level)) continue;
        if (!topic.isBlank() && !s(e, "topic").equalsIgnoreCase(topic)) continue;
        if (usesOneInProduct(e)) continue;
        unique.putIfAbsent(s(e, "expression").replaceAll("\\s+", "") + ":" + n(e, "answer"), e);
      }
    }
    require(
        unique.size() >= count,
        "Недостаточно уникальных примеров для категории "
            + category
            + (competitionCategory.isEmpty() ? "" : " · «" + CompetitionCategory.label(competitionCategory) + "»")
            + ": найдено "
            + unique.size()
            + ", нужно "
            + count);
    var pool = new ArrayList<>(unique.values());
    Collections.shuffle(pool, random);
    return new ArrayList<>(pool.subList(0, count));
  }

  public List<Map<String, Object>> selectRules(
      Map<String, Object> u,
      List<String> ids,
      String category,
      String competitionCategory,
      List<Rule> rules) {
    auth.staff(u);
    var pool = new LinkedHashMap<String, Map<String, Object>>();
    for (String lid : ids) {
      var library = db.one("select * from libraries where id=?", lid);
      requireLibraryAccess(u, library);
      if (!competitionCategory.equals(s(library, "competition_category"))) continue;
      for (var example : db.list(
          "select * from library_examples where library_id=? and category=?", lid, category)) {
        if (example.get("digits") == null || example.get("terms") == null
            || example.get("operation") == null) continue;
        String key = s(example, "expression").replaceAll("\\s+", "") + ":" + n(example, "answer");
        pool.putIfAbsent(key, example);
      }
    }
    var selected = new ArrayList<Map<String, Object>>();
    var used = new HashSet<String>();
    for (Rule rule : rules) {
      var candidates = pool.entrySet().stream()
          .filter(entry -> !used.contains(entry.getKey()))
          .filter(entry -> n(entry.getValue(), "digits") == rule.digits())
          .filter(entry -> n(entry.getValue(), "terms") == rule.terms())
          .filter(entry -> operationMatches(s(entry.getValue(), "operation"), rule.operation()))
          .filter(entry -> !usesOneInProduct(entry.getValue()))
          .map(Map.Entry::getValue)
          .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
      Collections.shuffle(candidates, random);
      require(candidates.size() >= rule.count(),
          "Недостаточно примеров для " + rule.digits() + "D " + rule.terms() + "R · "
              + operationLabel(rule.operation()) + ": найдено " + candidates.size()
              + ", нужно " + rule.count());
      for (var example : candidates.subList(0, rule.count())) {
        String key = s(example, "expression").replaceAll("\\s+", "") + ":" + n(example, "answer");
        used.add(key);
        selected.add(example);
      }
    }
    return selected;
  }

  private boolean usesOneInProduct(Map<String, Object> example) {
    String operation = Objects.toString(example.get("operation"), "");
    if (!Set.of("MULTIPLICATION", "DIVISION").contains(operation)) return false;
    var matcher = java.util.regex.Pattern
        .compile("^\\s*(-?\\d+)\\s*[×x*÷/]\\s*(-?\\d+)\\s*$")
        .matcher(s(example, "expression"));
    return matcher.matches()
        && (Math.abs(Long.parseLong(matcher.group(1))) == 1
            || Math.abs(Long.parseLong(matcher.group(2))) == 1);
  }

  private boolean operationMatches(String actual, String requested) {
    if (actual.equals(requested)) return true;
    return actual.equals("MIXED")
        && (requested.equals("ADDITION") || requested.equals("SUBTRACTION"));
  }

  private String operationLabel(String operation) {
    return switch (operation) {
      case "ADDITION" -> "сложение";
      case "SUBTRACTION" -> "вычитание";
      case "MULTIPLICATION" -> "умножение";
      case "DIVISION" -> "деление";
      default -> operation;
    };
  }
}
