package ru.mentalarena;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Db {
  final JdbcTemplate jdbc;

  public Db(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          var md = rs.getMetaData();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            Object v = rs.getObject(i);
            if (v instanceof Timestamp t) v = t.toInstant();
            if (v instanceof OffsetDateTime t) v = t.toInstant();
            if (v instanceof java.sql.Date d) v = d.toLocalDate();
            m.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), v);
          }
          return m;
        },
        args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
    return rows.getFirst();
  }

  public Optional<Map<String, Object>> maybe(String sql, Object... args) {
    return list(sql, args).stream().findFirst();
  }

  public int run(String sql, Object... args) {
    return jdbc.update(
        sql,
        Arrays.stream(args)
            .map(v -> v instanceof Instant t ? OffsetDateTime.ofInstant(t, ZoneOffset.UTC) : v)
            .toArray());
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String s(Map<String, Object> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  public static int n(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).intValue();
  }

  public static boolean b(Map<String, Object> m, String k) {
    return Boolean.TRUE.equals(m.get(k));
  }

  public static Instant time(Map<String, Object> m, String k) {
    return (Instant) m.get(k);
  }

  public static void require(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public static void forbidden() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
  }
}
