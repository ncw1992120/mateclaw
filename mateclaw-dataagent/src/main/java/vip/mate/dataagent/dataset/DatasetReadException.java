package vip.mate.dataagent.dataset;

public class DatasetReadException extends RuntimeException {
    private final DatasetReadErrorCode code;

    public DatasetReadException(DatasetReadErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DatasetReadException(DatasetReadErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DatasetReadErrorCode code() {
        return code;
    }
}
