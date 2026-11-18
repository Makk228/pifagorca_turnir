package ru.mentalarena;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.mentalarena.Db.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:tests;NON_KEYWORDS=DAY,VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
      "spring.datasource.username=${TEST_DATABASE_USER:sa}",
      "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
      "app.jobs=false",
      "app.demo=false"
    })
@AutoConfigureMockMvc
class PlatformTest {
  @Autowired Db db;
  @Autowired Auth auth;
  @Autowired Marathons marathons;
  @Autowired Libraries libraries;
  @Autowired Assignments assignments;
  @Autowired Rankings rankings;
  @Autowired MailDelivery mail;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TestClock clock;
  Map<String, Object> parent, teacher, admin, other, child, child2, marathon;
  String mid, lid, aid;

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-11-05T09:00:00Z"));

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    public Instant instant() {
      return now.get();
    }

    void set(String t) {
      now.set(Instant.parse(t));
    }

    void advance(long seconds) {
      now.updateAndGet(t -> t.plusSeconds(seconds));
    }
  }

  @BeforeEach
  void setup() {
    db.run("update assignments set current_attempt=null");
    for (String table :
        List.of(
            "answers",
            "attempts",
            "reopen_events",
            "examples",
            "assignments",
            "tie_decisions",
            "library_examples",
            "libraries",
            "enrollments",
            "marathon_teachers",
            "marathon_syllabus_rules",
            "marathons",
            "children",
            "consents",
            "mail_outbox",
            "otp",
            "audit",
            "rate_limits",
            "users")) db.run("delete from " + table);
    clock.set("2026-11-05T09:00:00Z");
    parent = user("parent@example.test", "PARENT");
    teacher = user("teacher@example.test", "TEACHER");
    admin = user("admin@example.test", "ADMIN");
    other = user("other@example.test", "PARENT");
    child =
        marathons.child(
            parent,
            Map.of(
                "firstName",
                "Артём",
                "lastName",
                "Соколов",
                "birthYear",
                2018,
                "category",
                "BABY",
                "gender",
                "MALE"));
    child2 =
        marathons.child(
            parent,
            Map.of(
                "firstName",
                "София",
                "lastName",
                "Соколова",
                "birthYear",
                2020,
                "category",
                "BABY",
                "gender",
                "FEMALE"));
    marathon =
        marathons.create(
            teacher,
            Map.of(
                "title",
                "Тест",
                "description",
                "Марафон",
                "startDate",
                "2026-11-05",
                "days",
                6,
                "timezone",
                "Europe/Moscow",
                "published",
                true));
    mid = s(marathon, "id");
    marathons.update(teacher, mid, Map.of("status", "SCHEDULED"));
    for (var c : List.of(child, child2))
      marathons.join(parent, Map.of("childId", s(c, "id"), "code", s(marathon, "join_code")));
    aid = manual(child, 1);
  }

  Map<String, Object> user(String email, String role) {
    String uid = id();
    db.run(
        "insert into"
            + " users(id,email,full_name,password_hash,role,email_consent,ranking_consent,created_at)"
            + " values(?,?,?,?,?,true,true,?)",
        uid,
        email,
        "Пользователь " + role,
        auth.hashPassword("Password123!"),
        role,
        clock.instant());
    return db.one("select * from users where id=?", uid);
  }

  String manual(Map<String, Object> c, int day) {
    return assignments.manual(
        teacher,
        mid,
        Map.of(
            "childId",
            s(c, "id"),
            "day",
            day,
            "examples",
            List.of(
                Map.of("expression", "2 + 3", "answer", 5),
                Map.of("expression", "8 - 8", "answer", 0),
                Map.of("expression", "3 - 6", "answer", -3))));
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> examples(Map<String, Object> view) {
    return (List<Map<String, Object>>) view.get("examples");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> attempt(Map<String, Object> view) {
    return (Map<String, Object>) view.get("attempt");
  }

  Map<String, Object> start() {
    return assignments.start(parent, aid);
  }

  Map<String, Object> submitAll(String id) {
    var view = assignments.start(parent, id);
    var answers = new HashMap<String, String>();
    for (var e : db.list("select * from examples where assignment_id=?", id))
      answers.put(s(e, "id"), s(e, "answer"));
    return assignments.save(
        parent, id, Map.of("version", n(attempt(view), "version"), "answers", answers), true);
  }

  String upload(String content) throws Exception {
    return upload("", content);
  }

  String upload(String competitionCategory, String content) throws Exception {
    return s(
        libraries.upload(
            teacher,
            "Библиотека",
            competitionCategory,
            new MockMultipartFile(
                "file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8))),
        "id");
  }

  Map<String, Object> generation(List<String> libs, int count) {
    return Map.of(
        "childIds",
        List.of(s(child, "id"), s(child2, "id")),
        "day",
        2,
        "libraryIds",
        libs,
        "count",
        count,
        "level",
        "BASIC");
  }

  @Test
  void syllabusGenerationUsesAgeAtMarathonStartAndKeepsExamplesUnique() {
    db.run("update marathons set start_date=? where id=?", LocalDate.parse("2027-11-05"), mid);
    for (var c : List.of(child, child2)) {
      db.run("update children set birth_date=?,birth_year=? where id=?",
          LocalDate.parse("2020-11-06"), 2020, s(c, "id"));
      marathons.updateCompetitionCategory(
          teacher, mid, s(c, "id"), Map.of("competitionCategory", "NO_LAWS"));
    }
    String library = id();
    db.run(
        "insert into libraries(id,owner_id,name,created_at,competition_category) values(?,?,?,?,?)",
        library, s(teacher, "id"), "Excel NF", clock.instant(), "NO_LAWS");
    for (int i = 1; i <= 80; i++)
      db.run(
          "insert into library_examples"
              + "(id,library_id,category,topic,level,expression,answer,digits,terms,operation)"
              + " values(?,?,?,?,?,?,?,?,?,?)",
          id(), library, "BABY", "1D2R", "BASIC", i + " + 1", i + 1,
          1, 2, "ADDITION");

    var ids = assignments.generate(
        teacher,
        mid,
        Map.of(
            "childIds", List.of(s(child, "id"), s(child2, "id")),
            "day", 2,
            "libraryIds", List.of(library),
            "recipes", List.of(Map.of(
                "ageBand", "6_7",
                "category", "BABY",
                "competitionCategory", "NO_LAWS",
                "rules", List.of(Map.of(
                    "digits", 1, "terms", 2, "operation", "ADDITION", "count", 60))))));

    assertThat(ids).hasSize(2);
    for (String assignment : ids) {
      var generated = db.list("select expression from examples where assignment_id=?", assignment);
      assertThat(generated).hasSize(60);
      assertThat(generated.stream().map(e -> s(e, "expression")).distinct()).hasSize(60);
    }
  }

  @Test
  void syllabusOrderIsKeptAndProductsWithOneAreExcluded() {
    String orderedLibrary = id();
    db.run(
        "insert into libraries(id,owner_id,name,created_at,competition_category) values(?,?,?,?,?)",
        orderedLibrary, s(teacher, "id"), "Ordered", clock.instant(), "NO_LAWS");
    for (int i = 0; i < 4; i++) {
      db.run(
          "insert into library_examples"
              + "(id,library_id,category,topic,level,expression,answer,digits,terms,operation)"
              + " values(?,?,?,?,?,?,?,?,?,?)",
          id(), orderedLibrary, "BABY", "first", "BASIC", "A" + i, i, 1, 2, "ADDITION");
      db.run(
          "insert into library_examples"
              + "(id,library_id,category,topic,level,expression,answer,digits,terms,operation)"
              + " values(?,?,?,?,?,?,?,?,?,?)",
          id(), orderedLibrary, "BABY", "second", "BASIC", "B" + i, i, 1, 3, "ADDITION");
    }
    var ordered = libraries.selectRules(
        teacher, List.of(orderedLibrary), "BABY", "NO_LAWS",
        List.of(
            new Libraries.Rule(1, 2, "ADDITION", 3),
            new Libraries.Rule(1, 3, "ADDITION", 3)));
    assertThat(ordered).extracting(row -> s(row, "expression"))
        .allSatisfy(expression -> assertThat(expression).matches("[AB]\\d"));
    assertThat(ordered.subList(0, 3)).allSatisfy(row -> assertThat(s(row, "expression")).startsWith("A"));
    assertThat(ordered.subList(3, 6)).allSatisfy(row -> assertThat(s(row, "expression")).startsWith("B"));

    String productLibrary = id();
    db.run(
        "insert into libraries(id,owner_id,name,created_at,competition_category) values(?,?,?,?,?)",
        productLibrary, s(teacher, "id"), "Products", clock.instant(), "SINGLE_DIGIT");
    for (var example : List.of(
        Map.of("expression", "1 × 8", "answer", 8, "operation", "MULTIPLICATION"),
        Map.of("expression", "2 × 8", "answer", 16, "operation", "MULTIPLICATION"),
        Map.of("expression", "8 ÷ 1", "answer", 8, "operation", "DIVISION"),
        Map.of("expression", "8 ÷ 2", "answer", 4, "operation", "DIVISION")))
      db.run(
          "insert into library_examples"
              + "(id,library_id,category,topic,level,expression,answer,digits,terms,operation)"
              + " values(?,?,?,?,?,?,?,?,?,?)",
          id(), productLibrary, "SCHOOL", "product", "BASIC", example.get("expression"),
          example.get("answer"), 1, 2, example.get("operation"));
    var products = libraries.selectRules(
        teacher, List.of(productLibrary), "SCHOOL", "SINGLE_DIGIT",
        List.of(
            new Libraries.Rule(1, 2, "MULTIPLICATION", 1),
            new Libraries.Rule(1, 2, "DIVISION", 1)));
    assertThat(products).extracting(row -> s(row, "expression"))
        .containsExactly("2 × 8", "8 ÷ 2");
  }

  String header() {
    return "category,topic,level,expression,answer\n";
  }

  MockHttpSession session(Map<String, Object> u) {
    var s = new MockHttpSession();
    s.setAttribute("uid", u.get("id"));
    s.setAttribute("av", n(u, "auth_version"));
    return s;
  }

  @Test
  void timerIsServerOwnedAndRepeatedStartIsIdempotent() {
    var a = start();
    clock.advance(120);
    var b = start();
    assertThat(attempt(b).get("id")).isEqualTo(attempt(a).get("id"));
    assertThat(attempt(b).get("deadline")).isEqualTo(Instant.parse("2026-11-05T09:30:00Z"));
  }

  @Test
  void marathonHasItsOwnTaskDuration() {
    db.run("update marathons set task_duration_minutes=17 where id=?", mid);
    var started = start();
    assertThat(attempt(started).get("deadline")).isEqualTo(Instant.parse("2026-11-05T09:17:00Z"));
    assertThat(((Map<?, ?>) started.get("marathon")).get("taskDurationMinutes")).isEqualTo(17);
  }

  @Test
  void correctAnswersAreNotExposedBeforeCompletion() {
    assertThat(examples(assignments.get(parent, aid))).isEmpty();
    assertThat(examples(start())).allSatisfy(e -> assertThat(e).doesNotContainKey("answer"));
  }

  @Test
  void teacherCanEditAssignmentOnlyBeforeChildStartsIt() {
    assignments.update(
        teacher,
        aid,
        Map.of(
            "instruction", "Новая инструкция",
            "examples", List.of(
                Map.of("expression", "12 + 8", "answer", 20),
                Map.of("expression", "30 - 7", "answer", 23))));

    var edited = assignments.get(teacher, aid);
    assertThat(edited.get("editable")).isEqualTo(true);
    assertThat(s((Map<String, Object>) edited.get("assignment"), "instruction"))
        .isEqualTo("Новая инструкция");
    assertThat(examples(edited)).extracting(e -> s(e, "expression"))
        .containsExactly("12 + 8", "30 - 7");

    assignments.start(parent, aid);
    assertThat(assignments.get(teacher, aid).get("editable")).isEqualTo(false);
    assertThatThrownBy(() -> assignments.update(
        teacher,
        aid,
        Map.of("examples", List.of(Map.of("expression", "1 + 1", "answer", 2)))))
        .hasMessageContaining("Ребёнок уже открыл задание");
  }

  @Test
  void gradingDistinguishesZeroNegativeWrongAndBlank() {
    var v = start();
    var es = examples(v);
    var result =
        assignments.save(
            parent,
            aid,
            Map.of(
                "version",
                0,
                "answers",
                Map.of(s(es.get(0), "id"), "9", s(es.get(1), "id"), "0", s(es.get(2), "id"), "")),
            true);
    assertThat(attempt(result))
        .containsEntry("correct", 1)
        .containsEntry("errors", 1)
        .containsEntry("skipped", 1);
    assertThat(examples(result)).allSatisfy(e -> assertThat(e).containsKey("answer"));
  }

  @Test
  void negativeAnswerIsAccepted() {
    var v = start();
    var es = examples(v);
    var result =
        assignments.save(
            parent, aid, Map.of("version", 0, "answers", Map.of(s(es.get(2), "id"), "-3")), true);
    assertThat(attempt(result)).containsEntry("correct", 1);
  }

  @Test
  void duplicateSubmitDoesNotChangeScoreOrNotifyAgain() {
    var first = submitAll(aid);
    long notifications = db.list("select * from mail_outbox").size();
    var second = assignments.save(parent, aid, Map.of("version", 0, "answers", Map.of()), true);
    assertThat(attempt(second)).isEqualTo(attempt(first));
    assertThat(db.list("select * from mail_outbox")).hasSize((int) notifications);
  }

  @Test
  void deadlineRejectsLateAnswersAndGradesSavedValues() {
    var v = start();
    var eid = s(examples(v).getFirst(), "id");
    assignments.save(parent, aid, Map.of("version", 0, "answers", Map.of(eid, "5")), false);
    clock.advance(1800);
    var result =
        assignments.save(parent, aid, Map.of("version", 1, "answers", Map.of(eid, "99")), true);
    assertThat(result.get("status")).isEqualTo("EXPIRED");
    assertThat(attempt(result)).containsEntry("correct", 1).containsEntry("skipped", 2);
  }

  @Test
  void midnightCutsOffOrdinaryAttempt() {
    clock.set("2026-11-05T20:59:30Z");
    assertThat(attempt(start()).get("deadline")).isEqualTo(Instant.parse("2026-11-05T21:00:00Z"));
  }

  @Test
  void scheduledAndMissedDaysCannotStart() {
    String tomorrow = manual(child, 2);
    assertThatThrownBy(() -> assignments.start(parent, tomorrow))
        .isInstanceOf(ResponseStatusException.class);
    clock.advance(86400);
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("MISSED");
    assertThatThrownBy(() -> start()).isInstanceOf(ResponseStatusException.class);
    assertThat(assignments.start(parent, tomorrow).get("status")).isEqualTo("IN_PROGRESS");
  }

  @Test
  void optimisticVersionPreventsLostUpdates() {
    var v = start();
    String eid = s(examples(v).getFirst(), "id");
    assignments.save(parent, aid, Map.of("version", 0, "answers", Map.of(eid, "5")), false);
    assertThatThrownBy(
            () ->
                assignments.save(
                    parent, aid, Map.of("version", 0, "answers", Map.of(eid, "6")), false))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    assertThat(examples(assignments.get(parent, aid)).getFirst()).containsEntry("value", 5);
  }

  @Test
  void invalidAnswerRollsBackWholeSave() {
    var es = examples(start());
    assertThatThrownBy(
            () ->
                assignments.save(
                    parent,
                    aid,
                    Map.of(
                        "version",
                        0,
                        "answers",
                        Map.of(s(es.get(0), "id"), "5", s(es.get(1), "id"), "abc")),
                    false))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.list("select * from answers")).isEmpty();
  }

  @Test
  void foreignExampleCannotBeSubmitted() {
    start();
    assertThatThrownBy(
            () ->
                assignments.save(
                    parent, aid, Map.of("version", 0, "answers", Map.of(id(), "5")), false))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void parentCannotReadOtherFamilyOrTeacherTools() {
    assertThatThrownBy(() -> assignments.get(other, aid))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> marathons.create(parent, Map.of()))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> libraries.list(parent)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void teachersAreScopedToAssignedMarathons() {
    var outsider = user("79001110005", "TEACHER");
    var second = user("79001110006", "TEACHER");
    assertThatThrownBy(() -> assignments.get(outsider, aid))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> rankings.export(outsider, mid))
        .isInstanceOf(ResponseStatusException.class);
    marathons.teachers(admin, mid, List.of(s(outsider, "id"), s(second, "id")));
    assertThat(assignments.get(outsider, aid)).containsKey("examples");
    assertThat(assignments.get(second, aid)).containsKey("examples");
    @SuppressWarnings("unchecked")
    var assigned = (List<Map<String, Object>>) marathons.detail(admin, mid).get("teachers");
    assertThat(assigned).extracting(row -> s(row, "id"))
        .contains(s(teacher, "id"), s(outsider, "id"), s(second, "id"));
  }

  @Test
  void teacherCannotStartForChild() {
    assertThatThrownBy(() -> assignments.start(teacher, aid))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void reopenCreatesAuditedAttemptAndPreservesHistory() {
    submitAll(aid);
    String prior = s(attempt(assignments.get(parent, aid)), "id");
    clock.advance(86400);
    assignments.reopen(
        teacher, aid, Map.of("confirmed", true, "reason", "Подтверждён обрыв связи у участника"));
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("REOPENED");
    var fresh = start();
    assertThat(attempt(fresh).get("id")).isNotEqualTo(prior);
    assertThat(attempt(fresh).get("deadline")).isEqualTo(clock.instant().plusSeconds(1800));
    assertThat(examples(fresh))
        .allSatisfy(e -> assertThat(e).containsKey("value").doesNotContainKey("answer"));
    assertThat(db.list("select * from attempts where assignment_id=?", aid)).hasSize(2);
    assertThat(db.list("select * from reopen_events")).hasSize(1);
  }

  @Test
  void reopenRequiresConfirmedReason() {
    assertThatThrownBy(
            () ->
                assignments.reopen(
                    teacher, aid, Map.of("reason", "хочу ещё раз", "confirmed", false)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () -> assignments.reopen(teacher, aid, Map.of("reason", "сбой", "confirmed", true)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void cannotOverwriteStartedAssignment() {
    start();
    assertThatThrownBy(() -> manual(child, 1)).isInstanceOf(ResponseStatusException.class);
    assertThat(db.list("select * from examples where assignment_id=?", aid)).hasSize(3);
  }

  @Test
  void copyCreatesIndependentExamples() {
    String copied = assignments.copy(teacher, aid, Map.of("childId", s(child2, "id"), "day", 1));
    assertThat(copied).isNotEqualTo(aid);
    assertThat(db.list("select * from examples where assignment_id=?", copied)).hasSize(3);
  }

  @Test
  void importAndGenerationFilterDeduplicateAndRemainIndividual() throws Exception {
    String a =
        upload(
            "NO_LAWS",
            header()
                + "BABY,Счёт,BASIC,1 + 2,3\n"
                + "BABY,Счёт,BASIC,1 + 2,3\n"
                + "BABY,Счёт,BASIC,2 + 2,4\n"
                + "SCHOOL,Счёт,BASIC,100 + 2,102\n");
    for (var participant : List.of(child, child2))
      marathons.updateCompetitionCategory(
          teacher, mid, s(participant, "id"), Map.of("competitionCategory", "NO_LAWS"));
    var ids = assignments.generate(teacher, mid, generation(List.of(a), 2));
    assertThat(ids).hasSize(2);
    for (String id : ids)
      assertThat(db.list("select expression from examples where assignment_id=?", id))
          .extracting(e -> s(e, "expression"))
          .containsExactlyInAnyOrder("1 + 2", "2 + 2");
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(a), 3)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void importIsAtomicOnBadRow() throws Exception {
    assertThatThrownBy(() -> upload(header() + "BABY,Счёт,BASIC,1+2,3\nUNKNOWN,Счёт,BASIC,2+2,4\n"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.list("select * from libraries")).isEmpty();
    assertThat(db.list("select * from library_examples")).isEmpty();
  }

  @Test
  void malformedHeaderAndEmptyCsvAreRejected() {
    assertThatThrownBy(() -> upload("expression,answer\n1+2,3\n"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> upload(header())).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void foreignLibrariesAreForbidden() throws Exception {
    String source = upload(header() + "BABY,Счёт,BASIC,1+2,3\n");
    var outsider = user("79001110005", "TEACHER");
    assertThatThrownBy(() -> libraries.select(outsider, List.of(source), "BABY", "", "", "", 1))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void deletingLibraryPreservesAssignmentsAndChecksOwnership() throws Exception {
    for (var participant : List.of(child, child2))
      marathons.updateCompetitionCategory(
          teacher, mid, s(participant, "id"), Map.of("competitionCategory", "NO_LAWS"));
    String source = upload("NO_LAWS", header() + "BABY,Счёт,BASIC,1+2,3\n");
    String retained = upload("NO_LAWS", header() + "BABY,Счёт,BASIC,4+2,6\n");
    var ids = assignments.generate(teacher, mid, generation(List.of(source), 1));
    String path = "/api/libraries/" + source;
    var outsider = user("79001110005", "TEACHER");
    mvc.perform(delete(path).session(session(outsider)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).session(session(parent)).with(csrf()))
        .andExpect(status().isForbidden());

    var ts = session(teacher);
    mvc.perform(delete(path).session(ts).with(csrf())).andExpect(status().isOk());
    mvc.perform(delete(path).session(ts).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/libraries").session(ts))
        .andExpect(jsonPath("$[*].id", contains(retained)));
    assertThat(db.list("select id from library_examples where library_id=?", source)).isEmpty();
    assertThat(db.list("select id from audit where action='LIBRARY_DELETED' and entity_id=?", source))
        .hasSize(1);
    for (String assignment : ids)
      mvc.perform(get("/api/assignments/" + assignment).session(ts))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.examples[0].expression").value("1+2"));
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(source), 1)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void generationUsesOnlyLibrariesMatchingEachParticipantsCompetitionCategory() throws Exception {
    String preschool = s(child, "id"), school = s(child2, "id");
    db.run("update children set category='SCHOOL' where id=?", school);
    String endpoint = "/api/marathons/" + mid + "/participants/";
    String plusTen = "{\"competitionCategory\":\"LAWS_PLUS_10\"}";
    var ts = session(teacher);
    mvc.perform(patch(endpoint + preschool + "/competition-category").session(session(parent))
        .with(csrf()).contentType("application/json").content(plusTen))
        .andExpect(status().isForbidden());
    mvc.perform(patch(endpoint + school + "/competition-category").session(ts)
        .with(csrf()).contentType("application/json").content(plusTen))
        .andExpect(status().isBadRequest());
    mvc.perform(patch(endpoint + preschool + "/competition-category").session(ts)
        .with(csrf()).contentType("application/json").content(plusTen))
        .andExpect(status().isOk());
    marathons.updateCompetitionCategory(teacher, mid, school, Map.of("competitionCategory", "NO_LAWS"));
    mvc.perform(get("/api/marathons/" + mid).session(ts))
        .andExpect(jsonPath("$.children[?(@.category == 'BABY')].competition_category",
            contains("LAWS_PLUS_10")));

    assertThatThrownBy(() -> upload("LAWS_PLUS_10", header() + "SCHOOL,Счёт,BASIC,10 + 2,12\n"))
        .isInstanceOf(ResponseStatusException.class);
    String plus = upload("LAWS_PLUS_10", header() + "BABY,Счёт,BASIC,10 + 3,13\n");
    String noLaws = upload("NO_LAWS", header()
        + "BABY,Счёт,BASIC,1 + 2,3\nSCHOOL,Счёт,BASIC,2 + 2,4\n");
    String untagged = upload(header() + "SCHOOL,Счёт,BASIC,3 + 3,6\n");
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(plus, untagged), 1)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("не подходит выбранным участникам");
    assertThat(db.list("select id from assignments where day=2")).isEmpty();

    String duplicateNoLaws = upload("NO_LAWS", header()
        + "BABY,Счёт,BASIC,4 + 4,8\nSCHOOL,Счёт,BASIC,5 + 5,10\n");
    assertThatThrownBy(() -> assignments.generate(
        teacher, mid, generation(List.of(plus, noLaws, duplicateNoLaws), 1)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("ровно один файл библиотеки");

    var ids = assignments.generate(teacher, mid, generation(List.of(plus, noLaws), 1));
    assertThat(ids).hasSize(2);
    assertThat(db.one("select expression from examples where assignment_id=?", ids.get(0)))
        .containsEntry("expression", "10 + 3");
    assertThat(db.one("select expression from examples where assignment_id=?", ids.get(1)))
        .containsEntry("expression", "2 + 2");
  }

  @Test
  void assignmentCategoriesDoNotSplitRankingsOrHideParticipantsFromParents() {
    String firstId = s(child, "id"), secondId = s(child2, "id");
    db.run("update children set parent_id=? where id=?", s(other, "id"), secondId);
    marathons.updateCompetitionCategory(teacher, mid, firstId,
        Map.of("competitionCategory", "NO_LAWS"));
    marathons.updateCompetitionCategory(teacher, mid, secondId,
        Map.of("competitionCategory", "LAWS_5"));
    assertThat(rankings.get(teacher, mid))
        .extracting(r -> r.get("category"), r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple("BABY", 1, true), tuple("BABY", 1, true));
    submitAll(aid);
    assertThat(rankings.get(parent, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("rank"))
        .containsExactly(tuple(firstId, 1), tuple(secondId, 2));
    assertThat(rankings.get(teacher, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("rank"))
        .containsExactly(tuple(firstId, 1), tuple(secondId, 2));
  }

  @Test
  void rankingIncludesZeroAndMissedDays() {
    clock.advance(6 * 86400);
    var rows = rankings.get(teacher, mid);
    assertThat(rows)
        .hasSize(2)
        .allSatisfy(
            r ->
                assertThat(r)
                    .containsEntry("score", 0)
                    .containsEntry("missed_days", 6)
                    .containsEntry("tie", true)
                    .containsEntry("rank", 1));
  }

  @Test
  void parentRankingsSeparateCategoriesAndIncludeTheirOtherParticipants() {
    db.run("update children set category='SCHOOL' where id=?", s(child2, "id"));
    var peer =
        marathons.child(
            other,
            Map.of(
                "firstName", "Анна",
                "lastName", "Иванова",
                "birthYear", 2020,
                "category", "BABY",
                "gender", "FEMALE"));
    marathons.join(other, Map.of("childId", s(peer, "id"), "code", s(marathon, "join_code")));

    assertThat(rankings.get(parent, mid))
        .extracting(r -> r.get("category"), r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple("SCHOOL", 1, false), tuple("BABY", 1, true), tuple("BABY", 1, true));

    submitAll(aid);
    assertThat(rankings.get(parent, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("category"), r -> r.get("rank"))
        .containsExactly(
            tuple(s(child2, "id"), "SCHOOL", 1),
            tuple(s(child, "id"), "BABY", 1),
            tuple(s(peer, "id"), "BABY", 2));
    assertThat(rankings.get(other, mid))
        .extracting(r -> r.get("category"), r -> r.get("rank"))
        .containsExactly(tuple("BABY", 1), tuple("BABY", 2));
  }

  @Test
  void staffRankingsScopeTieDecisionsAndExportsToCategory() throws Exception {
    var school =
        marathons.child(
            parent,
            Map.of(
                "firstName", "Мария",
                "lastName", "Соколова",
                "birthYear", 2016,
                "category", "SCHOOL",
                "gender", "FEMALE"));
    marathons.join(parent, Map.of("childId", s(school, "id"), "code", s(marathon, "join_code")));
    clock.advance(6 * 86400);
    String firstId = s(child, "id"), secondId = s(child2, "id"), schoolId = s(school, "id");
    assertThat(rankings.get(teacher, mid))
        .extracting(r -> r.get("category"), r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple("SCHOOL", 1, false), tuple("BABY", 1, true), tuple("BABY", 1, true));
    rankings.notifyTies();
    assertThat(db.list("select event_key from mail_outbox where event_key like 'tie:%'"))
        .extracting(row -> s(row, "event_key"))
        .containsExactlyInAnyOrder("tie:" + mid + ":" + firstId + ":0", "tie:" + mid + ":" + secondId + ":0");

    String reason = "Очная встреча 12 ноября, протокол №1";
    assertThatThrownBy(
            () -> rankings.resolve(teacher, mid, Map.of(
                "childIds", List.of(schoolId, firstId, secondId), "reason", reason)))
        .isInstanceOf(ResponseStatusException.class);

    var oldOrder = List.of(schoolId, secondId, firstId);
    for (int i = 0; i < oldOrder.size(); i++)
      db.run(
          "insert into tie_decisions(marathon_id,child_id,rank,score,reason,actor_id,at)"
              + " values(?,?,?,?,?,?,?)",
          mid, oldOrder.get(i), i + 1, 0, reason, s(teacher, "id"), clock.instant());
    assertThat(rankings.get(teacher, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("rank"), r -> r.get("decision"))
        .containsExactly(tuple(schoolId, 1, ""), tuple(secondId, 1, reason), tuple(firstId, 2, reason));

    marathons.updateCompetitionCategory(teacher, mid, secondId,
        Map.of("competitionCategory", "NO_LAWS"));
    db.run("update tie_decisions set applies_to_category=false where child_id in (?,?)",
        firstId, secondId);
    assertThat(rankings.get(teacher, mid))
        .extracting(r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple(1, false), tuple(1, true), tuple(1, true));

    rankings.resolve(teacher, mid, Map.of("childIds", List.of(firstId, secondId), "reason", reason));
    marathons.updateCompetitionCategory(teacher, mid, firstId,
        Map.of("competitionCategory", "LAWS_5"));
    assertThat(rankings.get(parent, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple(schoolId, 1, false), tuple(firstId, 1, false), tuple(secondId, 2, false));
    var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
    try (var report = CSVParser.parse(rankings.export(teacher, mid).substring(1), format)) {
      assertThat(report.stream().filter(record -> record.get("День").equals("1")).toList())
          .extracting(record -> record.get("ФИО"), record -> record.get("Категория"), record -> record.get("Место"))
          .containsExactly(
              tuple("Соколова Мария", "SCHOOL", "1"),
              tuple("Соколов Артём", "BABY", "1"),
              tuple("Соколова София", "BABY", "2"));
    }
  }

  @Test
  void tieResolutionChangesOnlyPlacesAndInvalidatesAfterReopen() {
    String second = manual(child2, 1);
    submitAll(aid);
    submitAll(second);
    clock.advance(6 * 86400);
    rankings.resolve(
        teacher,
        mid,
        Map.of(
            "childIds",
            List.of(s(child2, "id"), s(child, "id")),
            "reason",
            "Очная встреча 12 ноября: протокол №1"));
    var rows = rankings.get(teacher, mid);
    assertThat(rows.getFirst())
        .containsEntry("child_id", s(child2, "id"))
        .containsEntry("score", 3)
        .containsEntry("rank", 1)
        .containsEntry("tie", false);
    assertThat(rankings.get(parent, mid))
        .extracting(r -> r.get("child_id"), r -> r.get("rank"), r -> r.get("tie"))
        .containsExactly(tuple(s(child2, "id"), 1, false), tuple(s(child, "id"), 2, false));
    assignments.reopen(
        teacher, aid, Map.of("confirmed", true, "reason", "Ошибка связи подтверждена журналом"));
    assertThat(db.list("select * from tie_decisions")).isEmpty();
    assertThatThrownBy(
            () ->
                rankings.resolve(
                    teacher,
                    mid,
                    Map.of(
                        "childIds",
                        List.of(s(child2, "id"), s(child, "id")),
                        "reason",
                        "Новый протокол встречи")))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void rankingPublicationAndConsentAreEnforced() {
    marathons.update(teacher, mid, Map.of("published", false));
    assertThatThrownBy(() -> rankings.get(parent, mid)).isInstanceOf(ResponseStatusException.class);
    marathons.update(teacher, mid, Map.of("published", true));
    auth.updateConsents(parent, Map.of("rankingConsent", false));
    assertThat(db.one("select email_consent from users where id=?", s(parent, "id")))
        .containsEntry("email_consent", true);
    assertThat(rankings.get(parent, mid))
        .allSatisfy(
            r ->
                assertThat(r)
                    .containsEntry("name", "Участник без публикации ФИО")
                    .doesNotContainKey("child_id"));
  }

  @Test
  void manualAssignmentsCanUseTheCurrentDayAfterTheFirstDayCloses() throws Exception {
    clock.advance(5 * 86400);
    String path = "/api/marathons/" + mid;
    var ts = session(teacher);
    mvc.perform(get(path).session(ts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignableDays", contains(6)));

    var payload = new HashMap<String, Object>();
    payload.put("childId", s(child2, "id"));
    payload.put("examples", List.of(
        Map.of("expression", "2 + 3", "answer", "5"),
        Map.of("expression", "9 - 12", "answer", "-3")));
    payload.put("day", 1);
    mvc.perform(post(path + "/assignments").session(ts).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());

    payload.put("day", 6);
    var created = mvc.perform(post(path + "/assignments").session(ts).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(payload)))
        .andExpect(status().isOk()).andReturn();
    String assignmentId = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/assignments/" + assignmentId).session(ts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignment.day").value(6))
        .andExpect(jsonPath("$.examples[*].answer", contains(5, -3)));
  }

  @Test
  void changingMarathonStartReschedulesUnstartedAssignmentsAndPreservesAttempts() throws Exception {
    String second = manual(child2, 1);
    var started = attempt(start());
    String path = "/api/marathons/" + mid;
    var ts = session(teacher);
    mvc.perform(patch(path).session(session(parent)).with(csrf())
        .contentType("application/json").content("{\"startDate\":\"2026-11-07\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch(path).session(ts).with(csrf())
        .contentType("application/json").content("{\"startDate\":\"2026-02-30\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(patch(path).session(ts).with(csrf())
        .contentType("application/json").content("{\"startDate\":\"2026-11-07\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(path).session(session(parent)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.marathon.start_date").value("2026-11-07"))
        .andExpect(jsonPath("$.marathon.effective_status").value("SCHEDULED"))
        .andExpect(jsonPath("$.assignments[?(@.id == '" + second + "')].opens_at",
            contains("2026-11-06T21:00:00Z")));

    var continued = attempt(assignments.start(parent, aid));
    assertThat(continued)
        .containsEntry("id", started.get("id"))
        .containsEntry("deadline", started.get("deadline"))
        .containsEntry("status", "IN_PROGRESS");
    assertThatThrownBy(() -> assignments.start(parent, second))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Задание сейчас недоступно");
    assertThat(attempt(assignments.save(parent, aid,
        Map.of("version", n(continued, "version"), "answers", Map.of()), true)))
        .containsEntry("status", "CHECKED");

    clock.set("2026-11-06T21:00:00Z");
    assertThat(attempt(assignments.start(parent, second))).containsEntry("status", "IN_PROGRESS");
  }

  @Test
  void cancellationBlocksSubmissions() {
    start();
    marathons.update(teacher, mid, Map.of("status", "CANCELLED"));
    assertThatThrownBy(
            () -> assignments.save(parent, aid, Map.of("version", 0, "answers", Map.of()), true))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void schedulerFinalizesWithoutBrowserAndDeduplicatesNotifications() {
    start();
    clock.advance(1801);
    assignments.tick();
    int messages = db.list("select * from mail_outbox").size();
    assignments.tick();
    assertThat(db.list("select * from mail_outbox")).hasSize(messages);
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("EXPIRED");
  }

  @Test
  void mailLocalTransportAndWithdrawal() {
    mail.deliver();
    assertThat(db.list("select * from mail_outbox"))
        .allSatisfy(r -> assertThat(r).containsEntry("status", "LOCAL"));
    auth.queue(s(parent, "id"), "new-notification", "тест", false);
    auth.updateConsents(parent, Map.of("emailConsent", false, "rankingConsent", true));
    mail.deliver();
    assertThat(db.one("select * from mail_outbox where event_key='new-notification'"))
        .containsEntry("status", "CANCELLED");
  }

  @Test
  void reportContainsDetailsAndPreventsFormulaInjection() throws Exception {
    submitAll(aid);
    String report = rankings.export(teacher, mid);
    assertThat(report).contains("Соколов Артём", "Ошибки", "Пропуски примеров", "CHECKED");
    assertThat(rankings.childExport(teacher, s(child, "id"), mid)).contains("2 + 3", "CORRECT");
    assertThat(Rankings.safeCell("=HYPERLINK(1)")).startsWith("'");
    assertThat(Rankings.safeCell("  +cmd")).startsWith("'");
  }

  @Test
  void concurrentStartCreatesOnlyOneAttempt() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var f1 = pool.submit(this::start);
      var f2 = pool.submit(this::start);
      assertThat(attempt(f1.get()).get("id")).isEqualTo(attempt(f2.get()).get("id"));
    }
    assertThat(db.list("select * from attempts where assignment_id=?", aid)).hasSize(1);
  }

  @Test
  void csrfAndAnonymousAccessAreProtected() throws Exception {
    mvc.perform(post("/api/children").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.csrfToken").isNotEmpty());
  }

  @Test
  void httpLoginRotatesSessionAndDoesNotExposeHash() throws Exception {
    var old = new MockHttpSession();
    mvc.perform(
            post("/api/login")
                .session(old)
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"parent@example.test\",\"password\":\"Password123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.password_hash").doesNotExist());
    assertThat(old.isInvalid()).isTrue();
  }

  @Test
  void httpOwnershipAndRoleChecks() throws Exception {
    mvc.perform(get("/api/assignments/" + aid).session(session(other)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").session(session(teacher)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").session(session(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].password_hash").doesNotExist());
    String cid = s(child, "id");
    db.run("update children set gender=null where id=?", cid);
    mvc.perform(
            patch("/api/children/" + cid + "/gender")
                .session(session(other))
                .with(csrf())
                .contentType("application/json")
                .content("{\"gender\":\"MALE\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/children/" + cid + "/gender")
                .session(session(parent))
                .with(csrf())
                .contentType("application/json")
                .content("{\"gender\":\"MALE\"}"))
        .andExpect(status().isOk());
    assertThat(db.one("select gender from children where id=?", cid)).containsEntry("gender", "MALE");
  }

  @Test
  void newChildCardStoresTheFullBirthDateAndItsYear() throws Exception {
    mvc.perform(
            post("/api/children")
                .session(session(parent))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "firstName", "Анна",
                    "lastName", "Соколова",
                    "birthDate", "2020-02-29",
                    "category", "BABY",
                    "gender", "FEMALE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.birth_date").value("2020-02-29"))
        .andExpect(jsonPath("$.birth_year").value(2020));
  }

  @Test
  void parentCanUpdateBirthDateAndTeacherSeesAgeChangeOnBirthday() throws Exception {
    String cid = s(child, "id");
    String path = "/api/children/" + cid + "/birth-date";
    String childPath = "$.children[?(@.id == '" + cid + "')]";
    var parentSession = session(parent);
    var teacherSession = session(teacher);
    String birthDate = "{\"birthDate\":\"2018-11-06\"}";
    mvc.perform(
            patch(path).session(session(other)).with(csrf())
                .contentType("application/json").content(birthDate))
        .andExpect(status().isForbidden());
    for (String invalid : List.of("2018-02-29", "2026-11-06"))
      mvc.perform(
              patch(path).session(parentSession).with(csrf())
                  .contentType("application/json").content(json.writeValueAsString(
                      Map.of("birthDate", invalid))))
          .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path).session(parentSession).with(csrf())
                .contentType("application/json").content(birthDate))
        .andExpect(status().isOk());
    mvc.perform(get("/api/dashboard").session(parentSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath(childPath + ".birth_date", contains("2018-11-06")));

    String marathonPath = "/api/marathons/" + mid;
    String agePath = childPath + ".age";
    mvc.perform(get(marathonPath).session(teacherSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath(agePath, contains(7)))
        .andExpect(jsonPath("$.children[*].birth_date").doesNotHaveJsonPath());
    clock.set("2026-11-05T21:00:00Z");
    mvc.perform(get(marathonPath).session(teacherSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath(agePath, contains(8)));

    mvc.perform(
            patch(path).session(parentSession).with(csrf())
                .contentType("application/json").content("{\"birthDate\":\"2017-04-06\"}"))
        .andExpect(status().isOk());
    assertThat(db.one("select birth_year from children where id=?", cid))
        .containsEntry("birth_year", 2017);
    mvc.perform(get(marathonPath).session(teacherSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath(agePath, contains(9)));
  }

  @Test
  void parentCanRemoveOnlyOwnChildCardWithoutErasingMarathonResults() throws Exception {
    String cid = s(child, "id");
    String path = "/api/children/" + cid;
    var completed = attempt(submitAll(aid));
    var parentSession = session(parent);
    mvc.perform(delete(path).session(parentSession)).andExpect(status().isForbidden());
    mvc.perform(delete(path).session(session(other)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).session(parentSession).with(csrf())).andExpect(status().isOk());
    mvc.perform(delete(path).session(parentSession).with(csrf())).andExpect(status().isOk());

    mvc.perform(get("/api/dashboard").session(parentSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.children.length()").value(1))
        .andExpect(jsonPath("$.children[0].id").value(s(child2, "id")));
    mvc.perform(get("/api/marathons/" + mid).session(parentSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignments").isEmpty());
    mvc.perform(get("/api/assignments/" + aid).session(parentSession))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/assignments/" + aid).session(session(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempt.id").value(s(completed, "id")))
        .andExpect(jsonPath("$.attempt.correct").value(n(completed, "correct")));
    assertThatThrownBy(() -> manual(child, 2))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Карточка ребёнка удалена");
    assertThat(rankings.get(parent, mid))
        .anySatisfy(row -> assertThat(row)
            .containsEntry("name", "Участник без публикации ФИО")
            .containsEntry("score", n(completed, "correct"))
            .doesNotContainKey("child_id"));
    assertThat(db.list("select id from audit where action='CHILD_DELETED' and entity_id=?", cid))
        .hasSize(1);
    mvc.perform(delete("/api/children/" + s(child2, "id")).session(parentSession).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/dashboard").session(parentSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.children").isEmpty())
        .andExpect(jsonPath("$.marathons").isEmpty());
  }

  @Test
  void registrationAndOtpRecoveryInvalidateSessions() throws Exception {
    auth.register(
        Map.of(
            "email",
            "new.parent@example.test",
            "fullName",
            "Новый Родитель",
            "password",
            "Test12",
            "terms",
            true,
            "personalData",
            true,
            "participation",
            true,
            "emailConsent",
            true),
        "ip-test");
    var u = db.one("select * from users where email='new.parent@example.test'");
    assertThat(s(u, "password_hash")).isNotEqualTo("Test12");
    assertThat(u).containsEntry("email_consent", true);
    assertThat(db.list("select * from consents where user_id=?", s(u, "id"))).hasSize(5);
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"new.parent@example.test\",\"password\":\"Test12\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").doesNotExist());
    assertThatThrownBy(
            () -> auth.requestOtp(Map.of("email", s(u, "email"), "purpose", "VERIFY"), "ip-test"))
        .isInstanceOf(ResponseStatusException.class);
    var session = session(u);
    clock.advance(1);
    auth.requestOtp(Map.of("email", s(u, "email"), "purpose", "RESET"), "ip-test");
    String code = codeFor(u);
    auth.verifyOtp(
        Map.of(
            "email",
            s(u, "email"),
            "purpose",
            "RESET",
            "code",
            code,
            "newPassword",
            "NewStrongPass123!"));
    mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isUnauthorized());
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of(
                        "email",
                        s(u, "email"),
                        "purpose",
                        "RESET",
                        "code",
                        code,
                        "newPassword",
                        "NewStrongPass123!")))
        .isInstanceOf(ResponseStatusException.class);
  }

  String codeFor(Map<String, Object> u) {
    return s(
            db.list(
                    "select * from mail_outbox where user_id=? and service=true order by created_at"
                        + " desc",
                    s(u, "id"))
                .getFirst(),
            "body")
        .replaceAll(".*?(\\d{6}).*", "$1");
  }

  @Test
  void wrongOtpAttemptsPersistAndLock() {
    auth.requestOtp(Map.of("email", s(parent, "email"), "purpose", "RESET"), "ip");
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(
              () ->
                  auth.verifyOtp(
                      Map.of("email", s(parent, "email"), "purpose", "RESET", "code", "bad",
                          "newPassword", "NewPassword123!")))
          .isInstanceOf(ResponseStatusException.class);
    assertThat(db.one("select * from otp where email=?", s(parent, "email")))
        .containsEntry("tries", 5);
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of(
                        "email", s(parent, "email"), "purpose", "RESET", "code", codeFor(parent),
                        "newPassword", "NewPassword123!")))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void limitsAndUnknownEmailDoNotLeakAccount() {
    for (int i = 0; i < 3; i++)
      auth.requestOtp(Map.of("email", "unknown@example.test", "purpose", "RESET"), "unknown-ip");
    assertThat(db.list("select * from otp")).isEmpty();
    assertThatThrownBy(
            () -> auth.requestOtp(Map.of("email", "unknown@example.test", "purpose", "RESET"), "unknown-ip"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("429");
  }

  @Test
  void httpStaffEndpointsAndAdministration() throws Exception {
    var ts = session(teacher);
    var ads = session(admin);
    mvc.perform(get("/api/dashboard").session(ts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.marathons[0].id").value(mid));
    mvc.perform(get("/api/marathons/" + mid).session(ts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.children.length()").value(2))
        .andExpect(jsonPath("$.children[0].age").isEmpty())
        .andExpect(jsonPath("$.children[1].age").isEmpty());
    mvc.perform(
            patch("/api/marathons/" + mid)
                .session(ts)
                .with(csrf())
                .contentType("application/json")
                .content("{\"published\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/marathons/" + mid + "/ranking").session(ts)).andExpect(status().isOk());
    mvc.perform(get("/api/marathons/" + mid + "/export").session(ts))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"));
    mvc.perform(
            get("/api/children/" + s(child, "id") + "/export").param("marathonId", mid).session(ts))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/audit").session(ads)).andExpect(status().isOk());
    mvc.perform(get("/api/admin/mail").session(ads))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").doesNotExist());
    mvc.perform(
            patch("/api/admin/users/" + s(other, "id"))
                .session(ads)
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"TEACHER\",\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/marathons/" + mid + "/teachers")
                .session(ads)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("userId", s(other, "id")))))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/admin/users/" + s(admin, "id"))
                .session(ads)
                .with(csrf())
                .contentType("application/json")
                .content("{\"role\":\"PARENT\",\"enabled\":false}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyAdminCanDeleteMarathonAndAllItsCompetitionData() throws Exception {
    submitAll(aid);
    db.run(
        "insert into marathon_syllabus_rules"
            + "(marathon_id,age_band,category,competition_category,position,digits,terms,operation,example_count)"
            + " values(?,?,?,?,?,?,?,?,?)",
        mid, "6_7", "BABY", "NO_LAWS", 1, 1, 2, "ADDITION", 60);
    db.run(
        "insert into tie_decisions"
            + "(marathon_id,child_id,rank,score,reason,actor_id,at) values(?,?,?,?,?,?,?)",
        mid, s(child, "id"), 1, 3, "Очная встреча проведена", s(teacher, "id"), clock.instant());
    String path = "/api/marathons/" + mid;

    mvc.perform(delete(path).session(session(parent)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).session(session(teacher)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).session(session(admin)).with(csrf()))
        .andExpect(status().isOk());

    assertThat(db.list("select * from marathons where id=?", mid)).isEmpty();
    assertThat(db.list("select * from marathon_teachers where marathon_id=?", mid)).isEmpty();
    assertThat(db.list("select * from enrollments where marathon_id=?", mid)).isEmpty();
    assertThat(db.list("select * from assignments where marathon_id=?", mid)).isEmpty();
    assertThat(db.list("select * from attempts where assignment_id=?", aid)).isEmpty();
    assertThat(db.list("select * from examples where assignment_id=?", aid)).isEmpty();
    assertThat(db.list("select * from marathon_syllabus_rules where marathon_id=?", mid)).isEmpty();
    assertThat(db.list("select * from tie_decisions where marathon_id=?", mid)).isEmpty();
    assertThat(db.list("select * from children where id in (?,?)", s(child, "id"), s(child2, "id")))
        .hasSize(2);
    assertThat(db.list("select * from audit where action='MARATHON_DELETED' and entity_id=?", mid))
        .hasSize(1);

    mvc.perform(delete(path).session(session(admin)).with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void httpAssignmentFlowAndLibraryUpload() throws Exception {
    var ts = session(teacher);
    var ps = session(parent);
    for (var participant : List.of(child, child2))
      marathons.updateCompetitionCategory(
          teacher, mid, s(participant, "id"), Map.of("competitionCategory", "NO_LAWS"));
    var file =
        new MockMultipartFile(
            "file",
            "examples.csv",
            "text/csv",
            (header() + "BABY,Счёт,BASIC,1+2,3\nBABY,Счёт,BASIC,2+2,4\n")
                .getBytes(StandardCharsets.UTF_8));
    var imported =
        mvc.perform(
                multipart("/api/libraries")
                    .file(file)
                    .param("name", "База")
                    .param("competitionCategory", "NO_LAWS")
                    .session(ts)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    String library = json.readTree(imported.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/libraries").session(ts))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].example_count").value(2));
    mvc.perform(
            post("/api/marathons/" + mid + "/generate")
                .session(ts)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(generation(List.of(library), 2))))
        .andExpect(status().isOk());
    mvc.perform(post("/api/assignments/" + aid + "/start").session(ps).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.examples[0].answer").doesNotExist());
    mvc.perform(
            put("/api/assignments/" + aid + "/answers")
                .session(ps)
                .with(csrf())
                .contentType("application/json")
                .content("{\"version\":0,\"answers\":{}}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/assignments/" + aid + "/submit")
                .session(ps)
                .with(csrf())
                .contentType("application/json")
                .content("{\"version\":1,\"answers\":{}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempt.skipped").value(3));
    mvc.perform(
            post("/api/assignments/" + aid + "/reopen")
                .session(ts)
                .with(csrf())
                .contentType("application/json")
                .content("{\"confirmed\":true,\"reason\":\"Подтверждён сбой подключения\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/assignments/" + aid + "/copy")
                .session(ts)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("childId", s(child2, "id"), "day", 3))))
        .andExpect(status().isOk());
  }

  @Test
  void otpExpirationAndPasswordPolicy() {
    auth.requestOtp(Map.of("email", s(parent, "email"), "purpose", "RESET"), "ip");
    String code = codeFor(parent);
    clock.advance(601);
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of("email", s(parent, "email"), "purpose", "RESET", "code", code,
                        "newPassword", "NewPassword123!")))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> Input.password(Map.of("p", "short"), "p"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> Input.password(Map.of("p", "short"), "p", 6))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> Input.password(Map.of("p", "я".repeat(40)), "p"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(Input.email("User@Example.RU")).isEqualTo("user@example.ru");
  }

  @Test
  void draftsCannotStartButPublishedAssignmentsNeedNoEmailVerification() {
    marathons.update(teacher, mid, Map.of("status", "DRAFT"));
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("DRAFT");
    assertThatThrownBy(this::start).isInstanceOf(ResponseStatusException.class);
    marathons.update(teacher, mid, Map.of("status", "SCHEDULED"));
    assertThat(start()).containsEntry("status", "IN_PROGRESS");
  }

  @Test
  void generationRollsBackAllChildrenWhenOneCategoryLacksExamples() throws Exception {
    db.run("update children set category='SCHOOL' where id=?", s(child2, "id"));
    for (var participant : List.of(child, child2))
      marathons.updateCompetitionCategory(
          teacher, mid, s(participant, "id"), Map.of("competitionCategory", "NO_LAWS"));
    String l = upload("NO_LAWS", header() + "BABY,Счёт,BASIC,1+2,3\n");
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(l), 1)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.list("select * from assignments where day=2")).isEmpty();
  }

  @Test
  void tieNotificationsAndFinalizationAreIdempotent() {
    clock.advance(6 * 86400);
    assignments.tick();
    rankings.notifyTies();
    int size = db.list("select * from mail_outbox").size();
    rankings.notifyTies();
    assignments.tick();
    assertThat(db.list("select * from mail_outbox")).hasSize(size);
    assertThat(db.list("select * from mail_outbox where event_key like 'tie:%'")).hasSize(2);
  }

  @Test
  void productionStartupRejectsUnsafeConfiguration() {
    var env = new org.springframework.mock.env.MockEnvironment();
    env.setActiveProfiles("prod");
    var bootstrap = new Bootstrap(db, auth, marathons, assignments, clock, env);
    var args = new org.springframework.boot.DefaultApplicationArguments(new String[0]);
    assertThatThrownBy(() -> bootstrap.run(args)).isInstanceOf(ResponseStatusException.class);
    env.withProperty("OPERATOR_NAME", "Тестовый оператор")
        .withProperty("OPERATOR_CONTACT", "qa@example.invalid")
        .withProperty("spring.datasource.url", "jdbc:h2:file:./data/test-prod")
        .withProperty("app.mail-mode", "smtp")
        .withProperty("app.mail-from", "mail@example.test")
        .withProperty("spring.mail.host", "smtp.example.test");
    assertThatCode(() -> bootstrap.run(args)).doesNotThrowAnyException();
    env.withProperty("app.demo", "true");
    assertThatThrownBy(() -> bootstrap.run(args)).isInstanceOf(ResponseStatusException.class);
    env.withProperty("app.demo", "false").withProperty("app.mail-mode", "local");
    assertThatThrownBy(() -> bootstrap.run(args)).isInstanceOf(ResponseStatusException.class);
  }
}
