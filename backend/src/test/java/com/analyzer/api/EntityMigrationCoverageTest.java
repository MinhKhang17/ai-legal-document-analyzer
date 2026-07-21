package com.analyzer.api;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMigrationCoverageTest {
    private static final Path ENTITY_CLASSES = Path.of("target/classes/com/analyzer/api/entity");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

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

            String allMigrationSql;
            try (var migrationFiles = Files.list(MIGRATIONS)) {
                allMigrationSql = migrationFiles.filter(path -> path.toString().endsWith(".sql"))
                        .sorted().map(path -> { try { return Files.readString(path); } catch (Exception e) { throw new IllegalStateException(e); } })
                        .reduce("", (left, right) -> left + "\n" + right).toLowerCase();
            }
            var seenColumns = new HashSet<String>();
            var metadata = sources.buildMetadata();
            metadata.getEntityBindings().forEach(binding -> {
                String table = binding.getTable().getName().toLowerCase();
                assertTrue(allMigrationSql.contains("create table if not exists " + table + " "),
                        () -> "Missing baseline CREATE TABLE for " + table);
                binding.getTable().getColumns().forEach(column -> {
                    String key = table + "." + column.getName().toLowerCase();
                    if (seenColumns.add(key)) {
                        assertTrue(allMigrationSql.contains(column.getName().toLowerCase()),
                                () -> "Missing reconciliation column " + key);
                    }
                });
            });
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
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
