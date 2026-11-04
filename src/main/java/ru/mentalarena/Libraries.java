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
  final SecureRandom random = new SecureRandom();

  Libraries(Db db, Auth auth, Clock clock) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
  }

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
  public Map<String, Object> upload(Map<String, Object> u, String name, MultipartFile file)
      throws IOException {
    auth.staff(u);
    require(
        name != null && !name.isBlank() && name.length() <= 120,
        "Название библиотеки: 1–120 символов");
    require(
        file.getSize() > 0 && file.getSize() <= 2 * 1024 * 1024, "Файл должен быть не больше 2 МБ");
    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
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
          "insert into libraries values(?,?,?,?)", lid, s(u, "id"), name.trim(), clock.instant());
      for (var r : parser) {
        require(r.isConsistent(), "Неверное количество колонок в строке " + r.getRecordNumber());
        var m = new HashMap<String, Object>(r.toMap());
        String cat = choice(m, "category", "BABY", "SCHOOL"),
            level = choice(m, "level", "BASIC", "MEDIUM", "HARD"),
            topic = text(m, "topic", 80),
            expression = text(m, "expression", 200);
        int answer = number(m, "answer", Integer.MIN_VALUE, Integer.MAX_VALUE);
        require(++count <= 10000, "Не более 10 000 примеров в файле");
        db.run(
            "insert into library_examples values(?,?,?,?,?,?,?)",
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

  public List<Map<String, Object>> select(
      Map<String, Object> u,
      List<String> ids,
      String category,
      String level,
      String topic,
      int count) {
    auth.staff(u);
    Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
    for (String lid : ids) {
      var library = db.one("select * from libraries where id=?", lid);
      if (!s(u, "role").equals("ADMIN") && !s(library, "owner_id").equals(s(u, "id"))) forbidden();
      for (var e :
          db.list(
              "select * from library_examples where library_id=? and category=?", lid, category)) {
        if (!level.isBlank() && !s(e, "level").equals(level)) continue;
        if (!topic.isBlank() && !s(e, "topic").equalsIgnoreCase(topic)) continue;
        unique.putIfAbsent(s(e, "expression").replaceAll("\\s+", "") + ":" + n(e, "answer"), e);
      }
    }
    require(
        unique.size() >= count,
        "Недостаточно уникальных примеров для категории "
            + category
            + ": найдено "
            + unique.size()
            + ", нужно "
            + count);
    var pool = new ArrayList<>(unique.values());
    Collections.shuffle(pool, random);
    return new ArrayList<>(pool.subList(0, count));
  }
}
