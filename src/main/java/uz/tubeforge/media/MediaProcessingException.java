package uz.tubeforge.media;

public class MediaProcessingException extends RuntimeException {
    private final String code;
    private final String userMessage;

    public MediaProcessingException(String code, String userMessage) {
        super(userMessage);
        this.code = code;
        this.userMessage = userMessage;
    }

    public MediaProcessingException(String code, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() { return code; }
    public String getUserMessage() { return userMessage; }
}
