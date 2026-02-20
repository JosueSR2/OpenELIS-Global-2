package org.openelisglobal.middleware;

public class MiddlewareReceiveResultResponse {

    private boolean success;
    private String message;
    private int receivedCount;
    private int persistedCount;
    private int readOnlyCount;

    public MiddlewareReceiveResultResponse() {
    }

    public MiddlewareReceiveResultResponse(boolean success, String message, int receivedCount, int persistedCount,
            int readOnlyCount) {
        this.success = success;
        this.message = message;
        this.receivedCount = receivedCount;
        this.persistedCount = persistedCount;
        this.readOnlyCount = readOnlyCount;
    }

    public static MiddlewareReceiveResultResponse success(String message, int receivedCount, int persistedCount,
            int readOnlyCount) {
        return new MiddlewareReceiveResultResponse(true, message, receivedCount, persistedCount, readOnlyCount);
    }

    public static MiddlewareReceiveResultResponse error(String message) {
        return new MiddlewareReceiveResultResponse(false, message, 0, 0, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getReceivedCount() {
        return receivedCount;
    }

    public void setReceivedCount(int receivedCount) {
        this.receivedCount = receivedCount;
    }

    public int getPersistedCount() {
        return persistedCount;
    }

    public void setPersistedCount(int persistedCount) {
        this.persistedCount = persistedCount;
    }

    public int getReadOnlyCount() {
        return readOnlyCount;
    }

    public void setReadOnlyCount(int readOnlyCount) {
        this.readOnlyCount = readOnlyCount;
    }
}
