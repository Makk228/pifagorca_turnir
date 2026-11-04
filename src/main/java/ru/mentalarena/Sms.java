package ru.mentalarena;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static ru.mentalarena.Db.*;
@Service
public class Sms {
 final Db db;final Clock clock;final String mode,key;final ObjectMapper json;final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 Sms(Db db,Clock clock,ObjectMapper json,@Value("${app.sms-mode}")String mode,@Value("${app.sms-api-key}")String key){this.db=db;this.clock=clock;this.mode=mode;this.key=key;this.json=json;}
 @Transactional public void deliver(){for(var candidate:db.list("select id from sms_outbox where status='PENDING' and next_at<=? order by created_at limit 30",clock.instant().atOffset(ZoneOffset.UTC))){var sms=db.one("select * from sms_outbox where id=? for update",s(candidate,"id"));if(!s(sms,"status").equals("PENDING"))continue;var u=db.one("select * from users where id=?",s(sms,"user_id"));if(!b(u,"enabled")||(!b(sms,"service")&&(!b(u,"sms_consent")||!b(u,"verified")))){db.run("update sms_outbox set status='CANCELLED',body='' where id=?",s(sms,"id"));continue;}if(mode.equals("local")){db.run("update sms_outbox set status='LOCAL' where id=?",s(sms,"id"));continue;}try{String form="api_id="+enc(key)+"&to="+enc(s(u,"phone"))+"&msg="+enc(s(sms,"body"))+"&json=1";var req=HttpRequest.newBuilder(URI.create("https://sms.ru/sms/send")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();var res=http.send(req,HttpResponse.BodyHandlers.ofString());var root=json.readTree(res.body());if(res.statusCode()!=200||root.path("status_code").asInt()!=100||root.path("sms").path(s(u,"phone")).path("status_code").asInt()!=100)throw new IOExceptionMarker();db.run("update sms_outbox set status='SENT',body='',attempts=attempts+1,last_error=null where id=?",s(sms,"id"));}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();int tries=n(sms,"attempts")+1;db.run("update sms_outbox set status=?,attempts=?,next_at=?,last_error=? where id=?",tries>=5?"FAILED":"PENDING",tries,clock.instant().plusSeconds(Math.min(3600,30L*(1L<<tries))),"Доставка не подтверждена провайдером",s(sms,"id"));}}}
 static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
 static class IOExceptionMarker extends Exception{}
}
