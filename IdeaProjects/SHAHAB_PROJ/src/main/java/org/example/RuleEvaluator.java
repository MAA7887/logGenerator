package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.sql.*;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class RuleEvaluator {

    private static final String TOPIC = "log-topic";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID = "log-regulator-group";

    private static final String DB_URL = "jdbc:mysql://localhost:3306/warning_db";
    private static final String DB_USER = "rule_user";
    private static final String DB_PASS = "securepassword";

    public static class RuleConfig {
        public int rule2_time = 5;
        public int rule2_cnt = 5;
        public int rule3_time = 5;
        public int rule3_cnt = 5;

        @Override
        public String toString() {
            return "RuleConfig{" +
                    "rule2_time=" + rule2_time +
                    ", rule2_cnt=" + rule2_cnt +
                    ", rule3_time=" + rule3_time +
                    ", rule3_cnt=" + rule3_cnt +
                    '}';
        }
        // No-arg constructor (optional, but implicit)
    }
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
        props.put("group.id", GROUP_ID);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        RuleConfig config = null;
        try {
            InputStream input = RuleEvaluator.class.getClassLoader().getResourceAsStream("config.json");
            ObjectMapper mapper = new ObjectMapper();
            config = mapper.readValue(input, RuleConfig.class);
        } catch (IOException e) {
            System.err.println("❌ Failed to load config.json");
            e.printStackTrace();
            System.exit(1); // stop program if config is required
        }
        System.out.println("config is : " + config);
        ObjectMapper objectMapper = new ObjectMapper();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        LogMessage log = objectMapper.readValue(record.value(), LogMessage.class);
                        applyRules(conn, log, config);
                        insertLog(conn, log);
                    } catch (Exception e) {
                        System.err.println("Failed to parse record: " + record.value());
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Database connection failed!");
            e.printStackTrace();
        }
    }

    private static void insertLog(Connection conn, LogMessage log) throws SQLException {
        String sql = "INSERT INTO logs (component_name, type, timestamp, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, log.getComponentName());
            stmt.setString(2, log.getLevel());
            stmt.setString(3, log.getTimestamp());
            stmt.setString(4, log.getMessage());
            stmt.executeUpdate();
        }
    }

    private static void applyRules(Connection conn, LogMessage log, RuleConfig config) {
        System.out.println("📄 Log from component [" + log.getComponentName() + "]: " + log);

        String sql = "SELECT COUNT(*) AS cnt FROM logs " +
                "WHERE component_name = ? AND timestamp BETWEEN ? AND ?";

        // Define the time window: 1 hour before and 1 hour after the log timestamp
        try {
            Timestamp logTime = Timestamp.valueOf(log.getTimestamp());

            // Use ±1 hour window
            Timestamp endTime = Timestamp.valueOf(log.getTimestamp());
            Timestamp startTime = new Timestamp(endTime.getTime() - 5 * 60 * 1000); // 5 minutes earlier

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, log.getComponentName());
                stmt.setTimestamp(2, startTime);
                stmt.setTimestamp(3, endTime);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    System.out.println("Rule3 : 📊 Number of logs for component [" + log.getComponentName() + "] between " +
                            startTime + " and " + endTime + " is: " + cnt);
                    if (cnt >= config.rule3_cnt) {
                        System.out.println("⚠️ Rule 3 triggered: log count >= " + config.rule3_cnt);
                        String insertSql = "INSERT INTO rules (component_name, rule_type, timestamp, description) VALUES (?, ?, ?, ?)";

                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, log.getComponentName());
                            insertStmt.setString(2, "RULE_3");
                            insertStmt.setTimestamp(3, Timestamp.valueOf(log.getTimestamp()));
                            insertStmt.setString(4, "Rule 3 triggered: log count in last " + config.rule3_time +
                                    " minutes is " + cnt + ", which exceeds threshold " + config.rule3_cnt);
                            insertStmt.executeUpdate();
                        } catch (SQLException ex) {
                            System.err.println("❌ Failed to insert rule violation into Rules table.");
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to count logs for component: " + log.getComponentName());
            e.printStackTrace();
        }


        String countSql = "SELECT COUNT(*) AS cnt FROM logs " +
                "WHERE component_name = ? AND type = ? AND timestamp BETWEEN ? AND ?";
        String fetchMessagesSql = "SELECT message FROM logs " +
                "WHERE component_name = ? AND type = ? AND timestamp BETWEEN ? AND ? " +
                "ORDER BY timestamp DESC LIMIT 2";

        try {
            Timestamp logTime = Timestamp.valueOf(log.getTimestamp());
            Timestamp endTime = logTime;
            Timestamp startTime = new Timestamp(endTime.getTime() - config.rule2_time * 60 * 1000);

            // Count matching logs
            int cnt = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setString(1, log.getComponentName());
                stmt.setString(2, log.getLevel()); // Assuming level = type
                stmt.setTimestamp(3, startTime);
                stmt.setTimestamp(4, endTime);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    cnt = rs.getInt("cnt");
                }
            }

            if (cnt >= config.rule2_cnt) {
                // Fetch last 2 messages
                StringBuilder recentMessages = new StringBuilder();
                try (PreparedStatement stmt = conn.prepareStatement(fetchMessagesSql)) {
                    stmt.setString(1, log.getComponentName());
                    stmt.setString(2, log.getLevel());
                    stmt.setTimestamp(3, startTime);
                    stmt.setTimestamp(4, endTime);

                    ResultSet rs = stmt.executeQuery();
                    int msgNum = 1;
                    while (rs.next()) {
                        recentMessages.append("N ").append(msgNum).append(": ")
                                .append(rs.getString("message")).append(" - ");
                        msgNum++;
                    }
                }
                // Insert rule violation into Rules table
                String insertSql = "INSERT INTO rules (component_name, rule_type, timestamp, description) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    System.out.println("Rule 2 triggered.: " + cnt);
                    insertStmt.setString(1, log.getComponentName());
                    insertStmt.setString(2, "RULE_2");
                    insertStmt.setTimestamp(3, logTime);
                    insertStmt.setString(4, "Rule 2 triggered: log count for type '" + log.getLevel() + "' in last " +
                            config.rule2_time + " minutes is " + cnt + ", exceeding threshold " + config.rule2_cnt + ". Recent logs: " +
                            recentMessages.toString());
                    insertStmt.executeUpdate();
                } catch (SQLException ex) {
                    System.out.println("❌ Failed to insert Rule 2 violation into Rules table.");
                    ex.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to evaluate Rule 2 for component: " + log.getComponentName());
            e.printStackTrace();
        }

    }

}
