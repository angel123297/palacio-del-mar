package co.palaciodelmar.backend.exception;

public class ForbiddenAccessException extends IllegalArgumentException {
    public ForbiddenAccessException(String message) {
        super(message);
    }
}
