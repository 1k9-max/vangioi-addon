package com.example.vangioi.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MoonConfig {
    private static final String DIRECTORY_NAME = "VanGioiAddon";
    private static final String FILE_NAME = "moon.properties";
    private static final Path FILE = resolveFile();

    private MoonConfig() {
    }

    public static Path file() {
        return FILE;
    }

    public static Path meteorDirectory() {
        return FILE.getParent().resolve("meteor-client");
    }

    public static Path addonDirectory() {
        return FILE.getParent();
    }

    public static boolean isDisabled() {
        Properties properties = load();
        return Boolean.parseBoolean(properties.getProperty("moonoff", "false"));
    }

    public static void setDisabled(boolean disabled) {
        Properties properties = load();
        properties.setProperty("moonoff", Boolean.toString(disabled));
        properties.setProperty("updated", Long.toString(System.currentTimeMillis()));
        save(properties);
    }

    private static Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(FILE)) return properties;

        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static void save(Properties properties) {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Van Gioi Addon control state");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path resolveFile() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, "Documents", DIRECTORY_NAME, FILE_NAME);
    }
}
