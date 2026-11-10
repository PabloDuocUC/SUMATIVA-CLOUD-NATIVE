package cl.duoc.tickets.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Convierte las excepciones en respuestas JSON con el codigo HTTP correcto. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        return respuesta(HttpStatus.BAD_REQUEST, "Datos invalidos", campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> peticionIncorrecta(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST,
                "Peticion incorrecta: revisa el formato de los datos enviados", null);
    }

    @ExceptionHandler(AlmacenamientoException.class)
    public ResponseEntity<Map<String, Object>> almacenamiento(AlmacenamientoException e) {
        log.error("Error de almacenamiento: {}", e.getMessage(), e.getCause());
        return respuesta(HttpStatus.BAD_GATEWAY, e.getMessage(), null);
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje, Object detalle) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fecha", LocalDateTime.now());
        cuerpo.put("estado", estado.value());
        cuerpo.put("error", estado.getReasonPhrase());
        cuerpo.put("mensaje", mensaje);
        if (detalle != null) {
            cuerpo.put("detalle", detalle);
        }
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
