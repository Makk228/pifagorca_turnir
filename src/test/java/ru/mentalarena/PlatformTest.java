package ru.mentalarena;

import static org.assertj.core.api.Assertions.*;
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
      "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:tests;MODE=PostgreSQL;NON_KEYWORDS=DAY,VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
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
  @Autowired Sms sms;
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
            "marathons",
            "children",
            "consents",
            "sms_outbox",
            "otp",
            "audit",
            "rate_limits",
            "users")) db.run("delete from " + table);
    clock.set("2026-11-05T09:00:00Z");
    parent = user("79001110001", "PARENT");
    teacher = user("79001110002", "TEACHER");
    admin = user("79001110003", "ADMIN");
    other = user("79001110004", "PARENT");
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
                "BABY"));
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
                "BABY"));
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

  Map<String, Object> user(String phone, String role) {
    String uid = id();
    db.run(
        "insert into"
            + " users(id,phone,full_name,password_hash,role,verified,sms_consent,ranking_consent,created_at)"
            + " values(?,?,?,?,?,true,true,true,?)",
        uid,
        phone,
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
    return s(
        libraries.upload(
            teacher,
            "Библиотека",
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
  void correctAnswersAreNotExposedBeforeCompletion() {
    assertThat(examples(assignments.get(parent, aid))).isEmpty();
    assertThat(examples(start())).allSatisfy(e -> assertThat(e).doesNotContainKey("answer"));
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
    long notifications = db.list("select * from sms_outbox").size();
    var second = assignments.save(parent, aid, Map.of("version", 0, "answers", Map.of()), true);
    assertThat(attempt(second)).isEqualTo(attempt(first));
    assertThat(db.list("select * from sms_outbox")).hasSize((int) notifications);
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
    assertThatThrownBy(() -> assignments.get(outsider, aid))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> rankings.export(outsider, mid))
        .isInstanceOf(ResponseStatusException.class);
    marathons.teacher(admin, mid, s(outsider, "id"));
    assertThat(assignments.get(outsider, aid)).containsKey("examples");
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
            header()
                + "BABY,Счёт,BASIC,1 + 2,3\n"
                + "BABY,Счёт,BASIC,2 + 2,4\n"
                + "SCHOOL,Счёт,BASIC,100 + 2,102\n");
    String b = upload(header() + "BABY,Счёт,BASIC,1 + 2,3\nBABY,Счёт,HARD,9 + 9,18\n");
    var ids = assignments.generate(teacher, mid, generation(List.of(a, b), 2));
    assertThat(ids).hasSize(2);
    for (String id : ids)
      assertThat(db.list("select expression from examples where assignment_id=?", id))
          .extracting(e -> s(e, "expression"))
          .containsExactlyInAnyOrder("1 + 2", "2 + 2");
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(a, b), 3)))
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
    assertThatThrownBy(() -> libraries.select(outsider, List.of(source), "BABY", "", "", 1))
        .isInstanceOf(ResponseStatusException.class);
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
    auth.updateConsents(parent, Map.of("rankingConsent", false, "smsConsent", false));
    assertThat(rankings.get(parent, mid))
        .allSatisfy(
            r ->
                assertThat(r)
                    .containsEntry("name", "Участник без публикации ФИО")
                    .doesNotContainKey("child_id"));
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
    int messages = db.list("select * from sms_outbox").size();
    assignments.tick();
    assertThat(db.list("select * from sms_outbox")).hasSize(messages);
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("EXPIRED");
  }

  @Test
  void smsLocalTransportAndWithdrawal() {
    sms.deliver();
    assertThat(db.list("select * from sms_outbox"))
        .allSatisfy(r -> assertThat(r).containsEntry("status", "LOCAL"));
    auth.queue(s(parent, "id"), "new-notification", "тест", false);
    auth.updateConsents(parent, Map.of("smsConsent", false, "rankingConsent", true));
    sms.deliver();
    assertThat(db.one("select * from sms_outbox where event_key='new-notification'"))
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
                .content("{\"phone\":\"79001110001\",\"password\":\"Password123!\"}"))
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
  }

  @Test
  void registrationAndOtpRecoveryInvalidateSessions() throws Exception {
    auth.register(
        Map.of(
            "phone",
            "79009998888",
            "fullName",
            "Новый Родитель",
            "password",
            "StrongPass123!",
            "terms",
            true,
            "personalData",
            true,
            "participation",
            true),
        "ip-test");
    var u = db.one("select * from users where phone='79009998888'");
    assertThat(s(u, "password_hash")).isNotEqualTo("StrongPass123!");
    assertThat(db.list("select * from consents where user_id=?", s(u, "id"))).hasSize(5);
    auth.requestOtp(Map.of("phone", s(u, "phone"), "purpose", "VERIFY"), "ip-test");
    String code = codeFor(u);
    auth.verifyOtp(Map.of("phone", s(u, "phone"), "purpose", "VERIFY", "code", code));
    assertThat(db.one("select * from users where id=?", s(u, "id")))
        .containsEntry("verified", true);
    var session = session(u);
    clock.advance(1);
    auth.requestOtp(Map.of("phone", s(u, "phone"), "purpose", "RESET"), "ip-test");
    auth.verifyOtp(
        Map.of(
            "phone",
            s(u, "phone"),
            "purpose",
            "RESET",
            "code",
            codeFor(u),
            "newPassword",
            "NewStrongPass123!"));
    mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isUnauthorized());
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of(
                        "phone",
                        s(u, "phone"),
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
                    "select * from sms_outbox where user_id=? and service=true order by created_at"
                        + " desc",
                    s(u, "id"))
                .getFirst(),
            "body")
        .replaceAll(".*код (\\d{6}).*", "$1");
  }

  @Test
  void wrongOtpAttemptsPersistAndLock() {
    auth.requestOtp(Map.of("phone", s(parent, "phone"), "purpose", "VERIFY"), "ip");
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(
              () ->
                  auth.verifyOtp(
                      Map.of("phone", s(parent, "phone"), "purpose", "VERIFY", "code", "bad")))
          .isInstanceOf(ResponseStatusException.class);
    assertThat(db.one("select * from otp where phone=?", s(parent, "phone")))
        .containsEntry("tries", 5);
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of(
                        "phone", s(parent, "phone"), "purpose", "VERIFY", "code", codeFor(parent))))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void limitsAndUnknownPhoneDoNotLeakAccount() {
    for (int i = 0; i < 3; i++)
      auth.requestOtp(Map.of("phone", "79009999999", "purpose", "RESET"), "unknown-ip");
    assertThat(db.list("select * from otp")).isEmpty();
    assertThatThrownBy(
            () -> auth.requestOtp(Map.of("phone", "79009999999", "purpose", "RESET"), "unknown-ip"))
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
        .andExpect(jsonPath("$.children.length()").value(2));
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
    mvc.perform(get("/api/admin/sms").session(ads))
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
  void httpAssignmentFlowAndLibraryUpload() throws Exception {
    var ts = session(teacher);
    var ps = session(parent);
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
    auth.requestOtp(Map.of("phone", s(parent, "phone"), "purpose", "VERIFY"), "ip");
    String code = codeFor(parent);
    clock.advance(601);
    assertThatThrownBy(
            () ->
                auth.verifyOtp(
                    Map.of("phone", s(parent, "phone"), "purpose", "VERIFY", "code", code)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> Input.password(Map.of("p", "short"), "p"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> Input.password(Map.of("p", "я".repeat(40)), "p"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(Input.phone("+7 (900) 123-45-67")).isEqualTo("79001234567");
  }

  @Test
  void draftsAndUnverifiedPhonesCannotStart() {
    marathons.update(teacher, mid, Map.of("status", "DRAFT"));
    assertThat(assignments.get(parent, aid).get("status")).isEqualTo("DRAFT");
    assertThatThrownBy(this::start).isInstanceOf(ResponseStatusException.class);
    marathons.update(teacher, mid, Map.of("status", "SCHEDULED"));
    var unverified = new HashMap<>(parent);
    unverified.put("verified", false);
    assertThatThrownBy(() -> assignments.start(unverified, aid))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void generationRollsBackAllChildrenWhenOneCategoryLacksExamples() throws Exception {
    db.run("update children set category='SCHOOL' where id=?", s(child2, "id"));
    String l = upload(header() + "BABY,Счёт,BASIC,1+2,3\n");
    assertThatThrownBy(() -> assignments.generate(teacher, mid, generation(List.of(l), 1)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.list("select * from assignments where day=2")).isEmpty();
  }

  @Test
  void tieNotificationsAndFinalizationAreIdempotent() {
    clock.advance(6 * 86400);
    assignments.tick();
    rankings.notifyTies();
    int size = db.list("select * from sms_outbox").size();
    rankings.notifyTies();
    assignments.tick();
    assertThat(db.list("select * from sms_outbox")).hasSize(size);
    assertThat(db.list("select * from sms_outbox where event_key like 'tie:%'")).hasSize(2);
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
        .withProperty("spring.datasource.url", "jdbc:postgresql://db/schitay")
        .withProperty("app.sms-mode", "smsru")
        .withProperty("app.sms-api-key", "test-not-a-real-key");
    assertThatCode(() -> bootstrap.run(args)).doesNotThrowAnyException();
    env.withProperty("app.demo", "true");
    assertThatThrownBy(() -> bootstrap.run(args)).isInstanceOf(ResponseStatusException.class);
    env.withProperty("app.demo", "false").withProperty("app.sms-mode", "local");
    assertThatThrownBy(() -> bootstrap.run(args)).isInstanceOf(ResponseStatusException.class);
  }
}
