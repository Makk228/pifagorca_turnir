package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class Api {
  @org.springframework.beans.factory.annotation.Value(
      "${app.operator-name:Организатор демонстрационного марафона}")
  String operator;

  @org.springframework.beans.factory.annotation.Value(
      "${app.operator-contact:Обратитесь к педагогу}")
  String contact;

  final Auth auth;
  final Db db;
  final Marathons marathons;
  final Libraries libraries;
  final Assignments assignments;
  final Rankings rankings;
  final Clock clock;

  Api(
      Auth auth,
      Db db,
      Marathons marathons,
      Libraries libraries,
      Assignments assignments,
      Rankings rankings,
      Clock clock) {
    this.auth = auth;
    this.db = db;
    this.marathons = marathons;
    this.libraries = libraries;
    this.assignments = assignments;
    this.rankings = rankings;
    this.clock = clock;
  }

  @GetMapping("/public-config")
  Object publicConfig() {
    return Map.of("operator", operator, "contact", contact);
  }

  @GetMapping("/session")
  public Map<String, Object> session(HttpServletRequest req, CsrfToken csrf) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("csrfToken", csrf.getToken());
    out.put("serverTime", clock.instant());
    try {
      out.put("user", auth.safe(auth.current(req)));
    } catch (org.springframework.web.server.ResponseStatusException e) {
      out.put("user", null);
    }
    return out;
  }

  @PostMapping("/register")
  Object register(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return auth.register(b, r.getRemoteAddr());
  }

  @PostMapping("/login")
  Object login(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return auth.login(b, r);
  }

  @PostMapping("/logout")
  Object logout(HttpServletRequest r) {
    if (r.getSession(false) != null) r.getSession(false).invalidate();
    return Map.of("ok", true);
  }

  @PostMapping("/otp/request")
  Object otp(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.requestOtp(b, r.getRemoteAddr());
    return Map.of("ok", true);
  }

  @PostMapping("/otp/verify")
  Object verify(@RequestBody Map<String, Object> b) {
    auth.verifyOtp(b);
    return Map.of("ok", true);
  }

  @PostMapping("/consents")
  Object consents(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.updateConsents(auth.current(r), b);
    return Map.of("ok", true);
  }

  @GetMapping("/dashboard")
  Object dashboard(HttpServletRequest r) {
    return marathons.dashboard(auth.current(r));
  }

  @PostMapping("/children")
  Object child(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return marathons.child(auth.current(r), b);
  }

  @PostMapping("/join")
  Object join(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    auth.checkLimit("join:" + s(auth.current(r), "id"), 20, 600);
    marathons.join(auth.current(r), b);
    return Map.of("ok", true);
  }

  @PostMapping("/marathons")
  Object create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return marathons.create(auth.current(r), b);
  }

  @GetMapping("/marathons/{mid}")
  Object detail(@PathVariable String mid, HttpServletRequest r) {
    return marathons.detail(auth.current(r), mid);
  }

  @PatchMapping("/marathons/{mid}")
  Object update(
      @PathVariable String mid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    marathons.update(auth.current(r), mid, b);
    return Map.of("ok", true);
  }

  @PostMapping("/marathons/{mid}/teachers")
  Object teacher(
      @PathVariable String mid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    marathons.teacher(auth.current(r), mid, text(b, "userId", 36));
    return Map.of("ok", true);
  }

  @GetMapping("/libraries")
  Object libraries(HttpServletRequest r) {
    return libraries.list(auth.current(r));
  }

  @PostMapping("/libraries")
  Object upload(@RequestParam String name, @RequestParam MultipartFile file, HttpServletRequest r)
      throws IOException {
    return libraries.upload(auth.current(r), name, file);
  }

  @PostMapping("/marathons/{mid}/generate")
  Object generate(
      @PathVariable String mid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return Map.of("ids", assignments.generate(auth.current(r), mid, b));
  }

  @PostMapping("/marathons/{mid}/assignments")
  Object manual(
      @PathVariable String mid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return Map.of("id", assignments.manual(auth.current(r), mid, b));
  }

  @PostMapping("/assignments/{aid}/copy")
  Object copy(@PathVariable String aid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return Map.of("id", assignments.copy(auth.current(r), aid, b));
  }

  @GetMapping("/assignments/{aid}")
  Object assignment(@PathVariable String aid, HttpServletRequest r) {
    return assignments.get(auth.current(r), aid);
  }

  @PostMapping("/assignments/{aid}/start")
  Object start(@PathVariable String aid, HttpServletRequest r) {
    return assignments.start(auth.current(r), aid);
  }

  @PutMapping("/assignments/{aid}/answers")
  Object save(@PathVariable String aid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return assignments.save(auth.current(r), aid, b, false);
  }

  @PostMapping("/assignments/{aid}/submit")
  Object submit(
      @PathVariable String aid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return assignments.save(auth.current(r), aid, b, true);
  }

  @PostMapping("/assignments/{aid}/reopen")
  Object reopen(
      @PathVariable String aid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    assignments.reopen(auth.current(r), aid, b);
    return Map.of("ok", true);
  }

  @GetMapping("/marathons/{mid}/ranking")
  Object ranking(@PathVariable String mid, HttpServletRequest r) {
    return rankings.get(auth.current(r), mid);
  }

  @PostMapping("/marathons/{mid}/tie")
  Object tie(@PathVariable String mid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    rankings.resolve(auth.current(r), mid, b);
    return Map.of("ok", true);
  }

  @GetMapping("/marathons/{mid}/export")
  ResponseEntity<String> export(@PathVariable String mid, HttpServletRequest r) throws IOException {
    return csvResponse(rankings.export(auth.current(r), mid), "marathon.csv");
  }

  @GetMapping("/children/{cid}/export")
  ResponseEntity<String> childExport(
      @PathVariable String cid, @RequestParam String marathonId, HttpServletRequest r)
      throws IOException {
    return csvResponse(rankings.childExport(auth.current(r), cid, marathonId), "child.csv");
  }

  ResponseEntity<String> csvResponse(String csv, String name) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv);
  }

  @GetMapping("/admin/users")
  Object users(HttpServletRequest r) {
    auth.admin(auth.current(r));
    return db.list(
        "select id,phone,full_name,role,enabled,verified,created_at from users order by created_at"
            + " desc limit 500");
  }

  @PatchMapping("/admin/users/{uid}")
  @Transactional
  Object user(@PathVariable String uid, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    var u = auth.current(r);
    auth.admin(u);
    require(!uid.equals(s(u, "id")), "Нельзя изменить собственную роль или отключить себя");
    db.one("select id from users where id=?", uid);
    String role = choice(b, "role", "PARENT", "TEACHER", "ADMIN");
    require(b.containsKey("enabled"), "Укажите активность");
    db.run(
        "update users set role=?,enabled=?,auth_version=auth_version+1 where id=?",
        role,
        b(b, "enabled"),
        uid);
    auth.audit(s(u, "id"), "USER_UPDATED", uid, role + ", enabled=" + b(b, "enabled"));
    return Map.of("ok", true);
  }

  @GetMapping("/admin/audit")
  Object audit(HttpServletRequest r) {
    auth.admin(auth.current(r));
    return db.list(
        "select a.*,u.full_name as actor_name from audit a left join users u on u.id=a.actor_id"
            + " order by a.at desc limit 500");
  }

  @GetMapping("/admin/sms")
  Object sms(HttpServletRequest r) {
    auth.admin(auth.current(r));
    return db.list(
        "select id,event_key,status,attempts,created_at,last_error from sms_outbox order by"
            + " created_at desc limit 200");
  }
}
