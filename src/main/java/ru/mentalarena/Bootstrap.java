package ru.mentalarena;

import static ru.mentalarena.Db.*;

import java.time.*;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  final Db db;
  final Auth auth;
  final Marathons marathons;
  final Assignments assignments;
  final Clock clock;
  final Environment env;

  Bootstrap(
      Db db,
      Auth auth,
      Marathons marathons,
      Assignments assignments,
      Clock clock,
      Environment env) {
    this.db = db;
    this.auth = auth;
    this.marathons = marathons;
    this.assignments = assignments;
    this.clock = clock;
    this.env = env;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
    String phone = env.getProperty("app.admin-phone", ""),
        password = env.getProperty("app.admin-password", "");
    if (prod) {
      require(
          !env.getProperty("OPERATOR_NAME", "").isBlank()
              && !env.getProperty("OPERATOR_CONTACT", "").isBlank(),
          "Укажите OPERATOR_NAME и OPERATOR_CONTACT");
      require(
          env.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql:"),
          "Production требует PostgreSQL");
      require(
          env.getProperty("app.sms-mode", "").equals("smsru")
              && !env.getProperty("app.sms-api-key", "").isBlank(),
          "Production требует SMS_API_KEY и SMS_MODE=smsru");
      require(!env.getProperty("app.demo", Boolean.class, false), "Демо запрещено в production");
    }
    if (!phone.isBlank()) {
      Input.password(Map.of("p", password), "p");
      user(Input.phone(phone), "Администратор", password, "ADMIN");
    }
    if (prod)
      require(
          db.maybe("select id from users where role='ADMIN' and enabled=true").isPresent(),
          "Для первого запуска нужны ADMIN_PHONE и ADMIN_PASSWORD");
    if (env.getProperty("app.demo", Boolean.class, false)
        && db.maybe("select id from users where phone='79000000001'").isEmpty()) demo();
  }

  String user(String phone, String name, String password, String role) {
    var exists = db.maybe("select id from users where phone=?", phone);
    if (exists.isPresent()) return s(exists.get(), "id");
    String uid = id();
    db.run(
        "insert into"
            + " users(id,phone,full_name,password_hash,role,enabled,sms_consent,ranking_consent,created_at)"
            + " values(?,?,?,?,?,true,false,true,?)",
        uid,
        phone,
        name,
        auth.hashPassword(password),
        role,
        clock.instant());
    return uid;
  }

  void demo() {
    String parentId = user("79000000001", "Анна Соколова", "DemoParent2026!", "PARENT"),
        teacherId = user("79000000002", "Мария Орлова", "DemoTeacher2026!", "TEACHER");
    user("79000000003", "Администратор демо", "DemoAdmin2026!", "ADMIN");
    var parent = db.one("select * from users where id=?", parentId);
    var teacher = db.one("select * from users where id=?", teacherId);
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow")));
    var child =
        marathons.child(
            parent,
            Map.of(
                "lastName",
                "Соколов",
                "firstName",
                "Артём",
                "middleName",
                "",
                "birthDate",
                today.minusYears(8).toString(),
                "category",
                "SCHOOL",
                "gender",
                "MALE"));
    var child2 =
        marathons.child(
            parent,
            Map.of(
                "lastName",
                "Соколова",
                "firstName",
                "София",
                "middleName",
                "",
                "birthDate",
                today.minusYears(6).toString(),
                "category",
                "BABY",
                "gender",
                "FEMALE"));
    var marathon =
        marathons.create(
            teacher,
            Map.of(
                "title",
                "Осенний марафон",
                "description",
                "Шесть дней маленьких открытий и больших побед. Считаем в своём темпе и становимся"
                    + " увереннее каждый день.",
                "startDate",
                today.toString(),
                "days",
                6,
                "timezone",
                "Europe/Moscow",
                "published",
                true));
    marathons.update(teacher, s(marathon, "id"), Map.of("status", "SCHEDULED"));
    for (var c : List.of(child, child2))
      db.run("insert into enrollments(marathon_id,child_id) values(?,?)", s(marathon, "id"), s(c, "id"));
    String lid = id();
    db.run(
        "insert into libraries(id,owner_id,name,created_at) values(?,?,?,?)",
        lid,
        teacherId,
        "Сложение и вычитание · старт",
        clock.instant());
    for (String cat : List.of("BABY", "SCHOOL"))
      for (int i = 1; i <= 60; i++) {
        int a = cat.equals("BABY") ? i % 10 : i;
        int b = i / 10 + 1;
        db.run(
            "insert into library_examples(id,library_id,category,topic,level,expression,answer)"
                + " values(?,?,?,?,?,?,?)",
            id(),
            lid,
            cat,
            "Сложение",
            "BASIC",
            a + " + " + b,
            a + b);
      }
    for (int day = 1; day <= 6; day++)
      assignments.generate(
          teacher,
          s(marathon, "id"),
          Map.of(
              "childIds",
              List.of(s(child, "id"), s(child2, "id")),
              "libraryIds",
              List.of(lid),
              "day",
              day,
              "count",
              12,
              "instruction",
              "Внимательно прочитай пример и впиши ответ. У тебя всё получится!"));
  }
}
