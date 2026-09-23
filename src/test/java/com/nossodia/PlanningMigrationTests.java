package com.nossodia;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PlanningMigrationTests {
    @Test void backfillsExistingFamiliesWithoutInferringTheirLocation() {
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("6").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID family = UUID.randomUUID();
            jdbc.update("insert into nosso_dia.families(id,name,created_at,updated_at) values (?, 'Family', now(), now())", family);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("select timezone from nosso_dia.families where id = ?", String.class, family)).isEqualTo("UTC");
        }
    }
}
