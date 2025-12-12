package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.*;
import org.apache.commons.io.monitor.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileIngester {

    private static final String LOG_DIR = "/home/mmd/Desktop/Shahab_Project/logs/";
    private static final String TOPIC = "log-topic";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static String cleanFileName(String name) {
        // Remove invisible or non-ASCII characters
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[^\\x20-\\x7E]", "") // ASCII printable only
                .trim();
    }

    // Pattern to parse log line:
    // 2021-07-12 01:22:42,114 [ThreadName] INFO package.name.ClassName – msg
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}),\\s?(\\d{3}) \\[(.+?)] (\\S+) (\\S+) - (.+)$"
    );


    public FileIngester() {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        this.producer = new KafkaProducer<>(props);
    }

    public void processExistingFiles() {
        File folder = new File(LOG_DIR);
        File[] files = folder.listFiles((dir, name) -> cleanFileName(name.toLowerCase()).endsWith(".log"));
        if (files == null) return;

        for (File file : files) {
            processFile(file);
        }
    }

    public void watchNewFiles() throws Exception {
        FileAlterationObserver observer = new FileAlterationObserver(LOG_DIR);
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                if (cleanFileName(file.getName().toLowerCase()).endsWith(".log"))
                    processFile(file);
            }
        });
        FileAlterationMonitor monitor = new FileAlterationMonitor(2000, observer);
        monitor.start();
    }

    private void processFile(File file) {
        String componentName = extractComponentName(file.getName());
        String timestamp = extractTimestampFromFileName(file.getName());
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String content = new String(Files.readAllBytes(file.toPath()));
            Matcher matcher = LOG_LINE_PATTERN.matcher(content);
            if (matcher.find()) {
                // Extract groups from the regex
                //String timestamp = matcher.group(1); // "2021-07-12 01:22:42,114"
                // String threadName = matcher.group(2); // ThreadName (not used in LogMessage)
                String level = matcher.group(4); // INFO
                // String className = matcher.group(4); // package.name.ClassName (not used separately)
                String message = matcher.group(6).trim(); // msg
                System.out.println(componentName + ' ' + level + ' ' + message + ' ' + timestamp);
                LogMessage logMessage = new LogMessage(componentName, level, message, timestamp);
                String json = objectMapper.writeValueAsString(logMessage);
                producer.send(new ProducerRecord<>(TOPIC, componentName, json));
            } else {
                System.out.println ("bug :" + file.getName());
            }
            Files.delete(Paths.get(file.getAbsolutePath()));
            System.out.println("✅ Processed and deleted: " + file.getName());
        } catch (IOException e) {
            System.err.println("❌ Error processing file: " + file.getName());
            e.printStackTrace();
        }
    }

    private String extractComponentName(String fileName) {
        // Example filename: comp1-2021_07_12-01_55_55.log
        String cleaned = cleanFileName(fileName);
        int dashIndex = cleaned.indexOf('-');
        if (dashIndex > 0) {
            return cleaned.substring(0, dashIndex);
        }
        return "UNKNOWN_COMPONENT";
    }
    private String extractTimestampFromFileName(String fileName) {
        String cleaned = cleanFileName(fileName);
        // Example: comp1-2021_07_12-01_55_55.log
        // First, remove the component prefix (up to first '-')
        int firstDash = cleaned.indexOf('-');
        if (firstDash < 0) return "UNKNOWN_TIMESTAMP";

        // Then, extract the timestamp part (everything between first dash and .log)
        String timestampPart = cleaned.substring(firstDash + 1, cleaned.lastIndexOf('.'));
        // timestampPart: "2021_07_12-01_55_55"

        // Replace '_' with '-' for date part and ':' for time part:
        // Split date and time by '-'
        String[] parts = timestampPart.split("-");
        if (parts.length != 2) return "UNKNOWN_TIMESTAMP";

        String datePart = parts[0].replace('_', '-'); // 2021-07-12
        String timePart = parts[1].replace('_', ':'); // 01:55:55

        return datePart + " " + timePart;
    }


    public static void main(String[] args) throws Exception {
        FileIngester ingester = new FileIngester();
        ingester.processExistingFiles();
        ingester.watchNewFiles();
    }
}
