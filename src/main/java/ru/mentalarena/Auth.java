package ru.mentalarena;

import static ru.mentalarena.Db.*;
import static ru.mentalarena.Input.*;

import jakarta.servlet.http.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Auth {
  final Db db;
  final Clock clock;
  final PasswordEncoder passwords;
  final RateLimiter limits;
  final SecureRandom random = new SecureRandom();
  final String dummy;

  Auth(Db db, Clock clock, PasswordEncoder passwords, RateLimiter limits) {
    this.db = db;
    this.clock = clock;
    this.passwords = passwords;
    this.limits = limits;
    dummy = passwords.encode("constant-unused-dummy-password");
  }

  public String hashPassword(String password) {
    return passwords.encode(password);
  }

  public void checkLimit(String key, int max, int seconds) {
    limits.check(key, max, seconds);
  }

  public Map<String, Object> current(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session == null || session.getAttribute("uid") == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт");
    var u = db.one("select * from users where id=?", session.getAttribute("uid"));
    if (!b(u, "enabled") || !Objects.equals(n(u, "auth_version"), session.getAttribute("av"))) {
      session.invalidate();
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите снова");
    }
    return u;
  }

  public Map<String, Object> safe(Map<String, Object> u) {
    var copy = new LinkedHashMap<>(u);
    copy.remove("password_hash");
    copy.remove("auth_version");
    return copy;
  }

  public void staff(Map<String, Object> u) {
    if (!Set.of("TEACHER", "ADMIN").contains(s(u, "role"))) forbidden();
  }

  public void admin(Map<String, Object> u) {
    if (!s(u, "role").equals("ADMIN")) forbidden();
  }

  public void parent(Map<String, Object> u) {
    if (!s(u, "role").equals("PARENT")) forbidden();
  }

  void audit(String actor, String action, String entity, String detail) {
    db.run(
        "insert into audit values(?,?,?,?,?,?)",
        id(),
        actor,
        action,
        entity,
        detail,
        clock.instant());
  }

  void consent(String uid, String kind, boolean granted) {
    db.run(
        "insert into consents values(?,?,?,?,?,?)",
        id(),
        uid,
        kind,
        granted,
        "2026-11-05",
        clock.instant());
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> body, String ip) {
    limits.check("register:" + ip, 10, 3600);
    String phone = phone(text(body, "phone", 30)),
        pass = password(body, "password"),
        name = text(body, "fullName", 150);
    require(
        b(body, "terms") && b(body, "personalData") && b(body, "participation"),
        "Подтвердите обязательные согласия");
    require(
        db.maybe("select id from users where phone=?", phone).isEmpty(),
        "Не удалось зарегистрироваться. Попробуйте вход или восстановление");
    String uid = id();
    db.run(
        "insert into"
            + " users(id,phone,full_name,password_hash,role,sms_consent,ranking_consent,created_at)"
            + " values(?,?,?,?,?,?,?,?)",
        uid,
        phone,
        name,
        passwords.encode(pass),
        "PARENT",
        b(body, "smsConsent"),
        b(body, "rankingConsent"),
        clock.instant());
    for (String c :
        List.of("terms", "personalData", "participation", "smsConsent", "rankingConsent"))
      consent(uid, c, b(body, c));
    audit(uid, "REGISTER", uid, "Регистрация и согласия");
    return Map.of("ok", true);
  }

  @Transactional
  public Map<String, Object> login(Map<String, Object> body, HttpServletRequest request) {
    String phone = phone(text(body, "phone", 30));
    limits.check("login-ip:" + request.getRemoteAddr(), 60, 900);
    limits.check("login:" + phone, 10, 900);
    var found = db.maybe("select * from users where phone=?", phone);
    String hash = found.map(u -> s(u, "password_hash")).orElse(dummy);
    boolean match = passwords.matches(s(body, "password"), hash);
    if (!match || found.isEmpty() || !b(found.get(), "enabled"))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный телефон или пароль");
    var u = found.get();
    var old = request.getSession(false);
    if (old != null) old.invalidate();
    var session = request.getSession(true);
    session.setAttribute("uid", s(u, "id"));
    session.setAttribute("av", n(u, "auth_version"));
    audit(s(u, "id"), "LOGIN", s(u, "id"), "Вход");
    return safe(u);
  }

  @Transactional
  public void updateConsents(Map<String, Object> u, Map<String, Object> body) {
    parent(u);
    db.run(
        "update users set sms_consent=?,ranking_consent=? where id=?",
        b(body, "smsConsent"),
        b(body, "rankingConsent"),
        s(u, "id"));
    consent(s(u, "id"), "smsConsent", b(body, "smsConsent"));
    consent(s(u, "id"), "rankingConsent", b(body, "rankingConsent"));
    audit(s(u, "id"), "CONSENTS", s(u, "id"), "Обновлены согласия");
  }

  @Transactional
  public void requestOtp(Map<String, Object> body, String ip) {
    String phone = phone(text(body, "phone", 30)),
        purpose = choice(body, "purpose", "VERIFY", "RESET");
    limits.check("otp-ip:" + ip, 20, 3600);
    limits.check("otp:" + phone, 3, 600);
    var user = db.maybe("select * from users where phone=? and enabled=true", phone);
    if (user.isEmpty()) return;
    String code = String.format("%06d", random.nextInt(1_000_000));
    db.run("delete from otp where phone=? and purpose=?", phone, purpose);
    db.run(
        "insert into otp(phone,purpose,code_hash,expires_at) values(?,?,?,?)",
        phone,
        purpose,
        passwords.encode(code),
        clock.instant().plusSeconds(600));
    queue(
        s(user.get(), "id"),
        "otp:" + id(),
        "Считай: код " + code + ". Действует 10 минут. Никому не сообщайте.",
        true);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public void verifyOtp(Map<String, Object> body) {
    String phone = phone(text(body, "phone", 30)),
        purpose = choice(body, "purpose", "VERIFY", "RESET");
    String pass = purpose.equals("RESET") ? password(body, "newPassword") : null;
    var row = db.maybe("select * from otp where phone=? and purpose=? for update", phone, purpose);
    if (row.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Код недействителен");
    var r = row.get();
    require(
        n(r, "tries") < 5 && time(r, "expires_at").isAfter(clock.instant()),
        "Код истёк. Запросите новый");
    db.run("update otp set tries=tries+1 where phone=? and purpose=?", phone, purpose);
    require(passwords.matches(s(body, "code"), s(r, "code_hash")), "Код недействителен");
    if (purpose.equals("VERIFY")) db.run("update users set verified=true where phone=?", phone);
    else
      db.run(
          "update users set password_hash=?,auth_version=auth_version+1 where phone=?",
          passwords.encode(pass),
          phone);
    db.run("delete from otp where phone=? and purpose=?", phone, purpose);
    var u = db.one("select id from users where phone=?", phone);
    if (purpose.equals("VERIFY"))
      queue(
          s(u, "id"),
          "registration:" + s(u, "id"),
          "Считай: регистрация завершена, телефон подтверждён. Добро пожаловать!",
          false);
    audit(
        s(u, "id"),
        purpose.equals("VERIFY") ? "PHONE_VERIFIED" : "PASSWORD_RESET",
        s(u, "id"),
        "Подтверждено одноразовым кодом");
  }

  void queue(String uid, String key, String body, boolean service) {
    var u = db.one("select * from users where id=?", uid);
    if (!service && (!b(u, "sms_consent") || !b(u, "verified"))) return;
    if (db.maybe("select id from sms_outbox where event_key=?", key).isPresent()) return;
    db.run(
        "insert into sms_outbox(id,user_id,event_key,body,service,status,next_at,created_at)"
            + " values(?,?,?,?,?,'PENDING',?,?)",
        id(),
        uid,
        key,
        body,
        service,
        clock.instant(),
        clock.instant());
  }
}
