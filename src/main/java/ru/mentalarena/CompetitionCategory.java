package ru.mentalarena;

import static ru.mentalarena.Db.require;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

enum CompetitionCategory {
  NO_LAWS("Без законов"),
  LAWS_PLUS_10("Законы на +10", true),
  LAWS_10("Законы на 10"),
  LAWS_5("Законы на 5"),
  ALL_LAWS("Все законы"),
  SINGLE_DIGIT("Умножение и деление однозначные"),
  MULTI_DIGIT("Умножение и деление многозначные");

  final String label;
  final boolean preschoolOnly;

  CompetitionCategory(String label) {
    this(label, false);
  }

  CompetitionCategory(String label, boolean preschoolOnly) {
    this.label = label;
    this.preschoolOnly = preschoolOnly;
  }

  static void validate(String code, String category) {
    if (code.isEmpty()) return;
    var competition = find(code);
    require(
        !competition.preschoolOnly || !"SCHOOL".equals(category),
        "Категория «Законы на +10» доступна только дошкольникам");
  }

  private static CompetitionCategory find(String code) {
    try {
      return valueOf(code);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная категория соревнования");
    }
  }

  static String label(String code) {
    return code.isEmpty() ? "" : find(code).label;
  }

  static List<Map<String, Object>> options() {
    return Arrays.stream(values())
        .map(category -> Map.<String, Object>of(
            "code", category.name(), "label", category.label, "preschoolOnly", category.preschoolOnly))
        .toList();
  }
}
