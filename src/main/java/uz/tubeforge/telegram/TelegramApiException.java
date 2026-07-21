package uz.tubeforge.telegram;

public class TelegramApiException extends RuntimeException {
    private final int errorCode;
    private final int retryAfterSeconds;

    public TelegramApiException(int errorCode, String message) {
        this(errorCode, message, 0);
    }

    public TelegramApiException(int errorCode, String message, int retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
