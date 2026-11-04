package ru.mentalarena;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",e.getReason()==null?"Ошибка запроса":e.getReason()));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("error","Проверьте формат данных"));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(Exception e){return ResponseEntity.status(409).body(Map.of("error","Запись уже существует или связана с другими данными"));}
 @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> tooLarge(Exception e){return ResponseEntity.status(413).body(Map.of("error","Файл превышает 2 МБ"));}
}
