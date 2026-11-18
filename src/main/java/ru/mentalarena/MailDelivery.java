package ru.mentalarena;

import static ru.mentalarena.Db.*;

import java.time.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailDelivery {
  final Db db;
  final Clock clock;
  final ObjectProvider<JavaMailSender> senders;
  final String mode, from;

  MailDelivery(
      Db db,
      Clock clock,
      ObjectProvider<JavaMailSender> senders,
      @Value("${app.mail-mode:local}") String mode,
      @Value("${app.mail-from:}") String from) {
    this.db = db;
    this.clock = clock;
    this.senders = senders;
    this.mode = mode;
    this.from = from;
  }

  @Transactional
  public void deliver() {
    for (var candidate :
        db.list(
            "select id from mail_outbox where status='PENDING' and next_at<=? order by created_at"
                + " limit 30",
            clock.instant().atOffset(ZoneOffset.UTC))) {
      var mail = db.one("select * from mail_outbox where id=? for update", s(candidate, "id"));
      if (!s(mail, "status").equals("PENDING")) continue;
      var user = db.one("select * from users where id=?", s(mail, "user_id"));
      if (!b(user, "enabled") || (!b(mail, "service") && !b(user, "email_consent"))) {
        db.run("update mail_outbox set status='CANCELLED',body='' where id=?", s(mail, "id"));
        continue;
      }
      if (mode.equals("local")) {
        db.run("update mail_outbox set status='LOCAL' where id=?", s(mail, "id"));
        continue;
      }
      try {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) throw new IllegalStateException("SMTP не настроен");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(s(user, "email"));
        message.setSubject(b(mail, "service") ? "Код доступа · Считай" : "Уведомление · Считай");
        message.setText(s(mail, "body"));
        sender.send(message);
        db.run(
            "update mail_outbox set status='SENT',body='',attempts=attempts+1,last_error=null where"
                + " id=?",
            s(mail, "id"));
      } catch (Exception e) {
        int tries = n(mail, "attempts") + 1;
        db.run(
            "update mail_outbox set status=?,attempts=?,next_at=?,last_error=? where id=?",
            tries >= 5 ? "FAILED" : "PENDING",
            tries,
            clock.instant().plusSeconds(Math.min(3600, 30L * (1L << tries))),
            "Почтовый сервер не подтвердил доставку",
            s(mail, "id"));
      }
    }
  }
}
