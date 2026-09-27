package com.beehome;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationMigrationTests {
    @Test
    void preservesRefreshRotationChainsWhenAddingSessionIds() {
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("18").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID(), root = UUID.randomUUID(), rotated = UUID.randomUUID();
            UUID current = UUID.randomUUID(), otherSession = UUID.randomUUID();
            jdbc.update("insert into beehome.users(id, name, email, created_at, updated_at) values (?, 'Person', 'migration@example.com', now(), now())", user);
            for (UUID id : new UUID[]{root, rotated, current, otherSession}) {
                jdbc.update("insert into beehome.refresh_tokens(id, user_id, token_hash, created_at, expires_at) values (?, ?, ?, now(), now() + interval '1 day')",
                        id, user, id.toString());
            }
            jdbc.update("update beehome.refresh_tokens set replaced_by = ?, revoked_at = now() where id = ?", rotated, root);
            jdbc.update("update beehome.refresh_tokens set replaced_by = ?, revoked_at = now() where id = ?", current, rotated);

            Flyway.configure().dataSource(source).load().migrate();

            for (UUID id : new UUID[]{root, rotated, current}) {
                assertThat(jdbc.queryForObject("select session_id from beehome.refresh_tokens where id = ?", UUID.class, id)).isEqualTo(root);
            }
            assertThat(jdbc.queryForObject("select session_id from beehome.refresh_tokens where id = ?", UUID.class, otherSession))
                    .isEqualTo(otherSession);
        }
    }
}
