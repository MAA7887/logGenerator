package org.example;

public class LogMessage {
    private String componentName;
    private String level;
    private String message;
    private String timestamp; // ISO string format "yyyy-MM-dd HH:mm:ss,SSS"

    public LogMessage() {}

    public LogMessage(String componentName, String level, String message, String timestamp) {
        this.componentName = componentName;
        this.level = level;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "[" + componentName + "] [" + level + "] " + timestamp + " - " + message;
    }
}
