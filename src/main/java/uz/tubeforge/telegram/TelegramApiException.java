package uz.tubeforge.telegram;

public class TelegramApiException extends RuntimeException {
    private final int errorCode;

    public TelegramApiException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
