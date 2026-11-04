package ru.mentalarena;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.jobs", havingValue = "true", matchIfMissing = true)
public class Jobs {
  final Assignments assignments;
  final Rankings rankings;
  final Sms sms;

  Jobs(Assignments assignments, Rankings rankings, Sms sms) {
    this.assignments = assignments;
    this.rankings = rankings;
    this.sms = sms;
  }

  @Scheduled(fixedDelay = 15000)
  public void deadlines() {
    assignments.tick();
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void ties() {
    rankings.notifyTies();
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 5000)
  public void messages() {
    sms.deliver();
  }
}
