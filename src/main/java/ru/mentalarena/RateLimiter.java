package ru.mentalarena;

import static ru.mentalarena.Db.*;

import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RateLimiter {
  final Db db;
  final Clock clock;

  RateLimiter(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      noRollbackFor = ResponseStatusException.class)
  public synchronized void check(String key, int max, int seconds) {
    Instant now = clock.instant();
    var row = db.maybe("select * from rate_limits where bucket=? for update", key);
    if (row.isEmpty()) {
      db.run("insert into rate_limits values(?,1,?)", key, now);
      return;
    }
    var r = row.get();
    if (time(r, "window_start").plusSeconds(seconds).isBefore(now)) {
      db.run("update rate_limits set hits=1,window_start=? where bucket=?", now, key);
      return;
    }
    if (n(r, "hits") >= max)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток. Попробуйте позже");
    db.run("update rate_limits set hits=hits+1 where bucket=?", key);
  }
}
