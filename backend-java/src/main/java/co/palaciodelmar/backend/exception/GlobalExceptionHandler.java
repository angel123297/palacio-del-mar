package co.palaciodelmar.backend.exception;

import co.palaciodelmar.backend.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private String sanitizeMessage(String message, String defaultFallback) {
        if (message == null || message.isBlank()) {
            return defaultFallback != null ? defaultFallback : "Solicitud inválida";
        }
        String clean = message.trim();
        if (clean.length() > 300) {
            return clean.substring(0, 300) + "...";
        }
        return clean;
    }

    private String sanitizeMessage(String message) {
        return sanitizeMessage(message, "Solicitud inválida");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(sanitizeMessage(errors)));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParameters(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Parámetro de solicitud inválido o faltante"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("El cuerpo de la solicitud (JSON) es inválido o está malformado"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(sanitizeMessage(ex.getMessage())));
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenAccess(ForbiddenAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(sanitizeMessage(ex.getMessage(), "Acceso denegado")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Acceso denegado: no posees los permisos necesarios";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(sanitizeMessage(msg, "Acceso denegado")));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(sanitizeMessage(ex.getMessage(), "Recurso no encontrado")));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Credenciales inválidas"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException ex) {
        String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "No autorizado";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(sanitizeMessage(msg, "No autorizado")));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(sanitizeMessage(reason, "Error en la solicitud")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Recurso no encontrado"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("Método HTTP " + ex.getMethod() + " no soportado para este endpoint"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Tipo de contenido no soportado: usa application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Error no controlado en el servidor: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Error interno del servidor. Por favor intenta de nuevo."));
    }
}
