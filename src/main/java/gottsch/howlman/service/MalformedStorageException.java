package gottsch.howlman.service;

public class MalformedStorageException extends java.io.IOException {
    public MalformedStorageException(String filePath, Throwable cause) {
        super("Malformed JSON in " + filePath + ": " + cause.getMessage(), cause);
    }
}
