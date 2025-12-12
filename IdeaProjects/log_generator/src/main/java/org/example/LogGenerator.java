package org.example;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class LogGenerator {

    private static final String[] COMPONENTS = {"component1", "component2", "component3", "component4"};
    private static final String[] LEVELS = {"INFO", "WARN", "ERROR"};
    private static final String[] MESSAGES = {
            "CPU usage high", "Disk full", "NullPointerException in service", "Connection timeout", "Cache miss occurred"
    };
    private static final String LOG_DIR = "/home/mmd/Desktop/Shahab_Project/logs/";
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {
        new File(LOG_DIR).mkdirs(); // Ensure log directory exists

        while (true) {
            String component = COMPONENTS[random.nextInt(COMPONENTS.length)];
            String level = LEVELS[random.nextInt(LEVELS.length)];
            String message = MESSAGES[random.nextInt(MESSAGES.length)];
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss, SSS"));
            String thread = "main-thread";
            String className = "package.name." + capitalize(component) + "Service";

            String logLine = String.format("%s [%s] %s %s - %s", timestamp, thread, level, className, message);

            // File name like component1-2025_05_27-13_45_12.log
            String fileName = String.format("%s%s-%s.log",
                    LOG_DIR,
                    component,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss")));

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                writer.write(logLine);
                writer.newLine(); // optional, but good practice
            } catch (IOException e) {
                System.err.println("❌ Failed to write log file: " + e.getMessage());
            }

            System.out.println("✅ Log file created: " + fileName);
            Thread.sleep(5_000); // wait 10 seconds
        }
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
