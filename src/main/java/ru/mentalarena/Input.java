package ru.mentalarena;

import static ru.mentalarena.Db.*;

import java.util.*;

public final class Input {
  private Input() {}

  public static String text(Map<String, Object> m, String key, int max) {
    String v = s(m, key).trim();
    require(
        !v.isEmpty() && v.length() <= max,
        "Поле «" + key + "»: требуется от 1 до " + max + " символов");
    return v;
  }

  public static String optional(Map<String, Object> m, String key, int max) {
    String v = s(m, key).trim();
    require(v.length() <= max, "Слишком длинное поле " + key);
    return v;
  }

  public static int number(Map<String, Object> m, String key, int min, int max) {
    try {
      int v = Integer.parseInt(s(m, key));
      require(v >= min && v <= max, "Поле " + key + " вне диапазона");
      return v;
    } catch (NumberFormatException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Требуется целое число: " + key);
    }
  }

  public static String choice(Map<String, Object> m, String key, String... allowed) {
    String v = s(m, key);
    require(Arrays.asList(allowed).contains(v), "Некорректное значение " + key);
    return v;
  }

  public static List<String> ids(Map<String, Object> m, String key) {
    Object o = m.get(key);
    require(o instanceof List<?>, "Требуется список " + key);
    List<String> v = ((List<?>) o).stream().map(Object::toString).distinct().toList();
    require(!v.isEmpty() && v.size() <= 500, "Список " + key + " должен содержать 1–500 значений");
    return v;
  }

  public static String phone(String v) {
    String p = v.replaceAll("[ ()+-]", "");
    if (p.startsWith("8") && p.length() == 11) p = "7" + p.substring(1);
    require(p.matches("[1-9][0-9]{9,14}"), "Укажите телефон в международном формате");
    return p;
  }

  public static String password(Map<String, Object> m, String key) {
    String p = s(m, key);
    require(
        p.length() >= 10 && p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
        "Пароль: от 10 символов, до 72 байт");
    return p;
  }
}
