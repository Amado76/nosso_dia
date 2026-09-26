package com.beehome;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyMigrationTests {
    @Test void preservesHistoricalSubjectlessSessionsButRequiresSubjectsForNewRows() {
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("17").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID(), family = UUID.randomUUID(), member = UUID.randomUUID(), oldSession = UUID.randomUUID();
            jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Owner', ?, now(), now())", user, user + "@example.com");
            jdbc.update("insert into beehome.families(id,name,timezone,created_at,updated_at) values (?, 'Family', 'UTC', now(), now())", family);
            jdbc.update("insert into beehome.family_members(id,family_id,name,member_type,active,created_at,updated_at) values (?,?, 'Child','CHILD',true,now(),now())", member, family);
            String insert = """
                    insert into beehome.study_sessions(id,family_id,family_member_id,session_date,entry_mode,status,
                        accumulated_duration_seconds,created_by_user_id,created_at,updated_at)
                    values (?,?,?,current_date,'MANUAL','COMPLETED',60,?,now(),now())
                    """;
            jdbc.update(insert, oldSession, family, member, user);

            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("select subject_id from beehome.study_sessions where id=?", UUID.class, oldSession)).isNull();
            jdbc.update("update beehome.study_sessions set notes='Retained' where id=?", oldSession);
            assertThat(jdbc.queryForObject("select notes from beehome.study_sessions where id=?", String.class, oldSession)).isEqualTo("Retained");
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), family, member, user))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
