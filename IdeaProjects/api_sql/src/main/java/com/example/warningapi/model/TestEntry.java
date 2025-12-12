package com.example.warningapi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Rules")
public class TestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "component_name")
    private String componentName;

    @Column(name = "rule_type")
    private String ruleType;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ConstructorsRENAME TABLE Rules TO rules;

    public TestEntry() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Optional toString
    @Override
    public String toString() {
        return "RuleEntry{" +
                "id=" + id +
                ", componentName='" + componentName + '\'' +
                ", ruleType='" + ruleType + '\'' +
                ", timestamp=" + timestamp +
                ", description='" + description + '\'' +
                '}';
    }
}
