package com.analyzer.api;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMigrationCoverageTest {
    private static final Path ENTITY_CLASSES = Path.of("target/classes/com/analyzer/api/entity");
    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    @Test
    void everyJpaEntityTableAndColumnIsCoveredByMigrations() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            var sources = new MetadataSources(registry);
            try (var paths = Files.walk(ENTITY_CLASSES)) {
                paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().contains("$"))
                        .map(ENTITY_CLASSES::relativize)
                        .map(path -> "com.analyzer.api.entity." + path.toString()
                                .replace('\\', '.').replace('/', '.').replaceAll("\\.class$", ""))
                        .map(EntityMigrationCoverageTest::loadClass)
                        .filter(type -> type.isAnnotationPresent(Entity.class))
                        .forEach(sources::addAnnotatedClass);
            }

            // Every *.sql file counts, not just one hardcoded baseline/reconciliation pair: a
            // new column can land in its own dedicated migration (as V20260722_01 does for
            // customer_plans' snapshot columns) without ever needing to edit an already-applied
            // migration file just to keep this test passing. Whitespace (including newlines) is
            // collapsed to single spaces since different migrations format multi-column ALTER
            // TABLE statements across several lines.
            String allMigrationsSql = allMigrationSql().toLowerCase().replaceAll("\\s+", " ");
            var seenColumns = new HashSet<String>();
            var metadata = sources.buildMetadata();
            metadata.getEntityBindings().forEach(binding -> {
                String table = binding.getTable().getName().toLowerCase();
                assertTrue(allMigrationsSql.contains("create table if not exists " + table + " "),
                        () -> "Missing CREATE TABLE for " + table + " in any migration");
                binding.getTable().getColumns().forEach(column -> {
                    String key = table + "." + column.getName().toLowerCase();
                    if (seenColumns.add(key)) {
                        assertTrue(allMigrationsSql.contains(column.getName().toLowerCase()),
                                () -> "Missing column " + key + " in any migration");
                    }
                });
            });
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static String allMigrationSql() throws IOException {
        try (var paths = Files.list(MIGRATION_DIR)) {
            return paths.filter(path -> path.toString().endsWith(".sql"))
                    .sorted(Comparator.naturalOrder())
                    .map(EntityMigrationCoverageTest::readQuietly)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
