package com.beehome;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMemberMigrationTests {
    @Test
    void upgradesPopulatedBe02DatabaseAndUsesFamilyListIndexes() {
        // Separate database verifies the populated V5 upgrade through the current schema without touching Spring's test database.
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID(), family = UUID.randomUUID();
            jdbc.update("insert into nosso_dia.users(id, name, email, created_at, updated_at) values (?, 'Person', 'migration@example.com', now(), now())", user);
            jdbc.update("insert into nosso_dia.families(id, name, created_at, updated_at) values (?, 'Family', now(), now())", family);
            jdbc.update("insert into nosso_dia.family_memberships(id, family_id, user_id, role, created_at) values (?, ?, ?, 'OWNER', now())", UUID.randomUUID(), family, user);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("select role from beehome.family_memberships where family_id = ? and user_id = ?", String.class, family, user)).isEqualTo("OWNER");
            jdbc.update("insert into beehome.family_members(id, family_id, name, member_type, linked_user_id, created_at, updated_at) values (?, ?, 'Person', 'ADULT', ?, now(), now())", UUID.randomUUID(), family, user);
            jdbc.execute("insert into beehome.families(id, name, timezone, created_at, updated_at) select gen_random_uuid(), 'Plan fixture', 'UTC', now(), now() from generate_series(1, 1000)");
            jdbc.execute("insert into beehome.family_members(id, family_id, name, member_type, active, created_at, updated_at) select gen_random_uuid(), f.id, 'Person', case when n % 2 = 0 then 'ADULT' else 'CHILD' end, n % 3 <> 0, now(), now() from beehome.families f cross join generate_series(1, 10) n");
            jdbc.execute("analyze beehome.family_members");
            // V7 adds a same-family FK index that PostgreSQL may also choose for this bounded family lookup.
            for (String filter : new String[]{"", " and active = true", " and active = true and member_type = 'CHILD'", " and member_type = 'ADULT'"}) {
                String plan = String.join("\n", jdbc.queryForList("explain select * from beehome.family_members where family_id = ?" + filter + " order by created_at, id", String.class, family));
                assertThat(plan).contains("Index").containsAnyOf("family_members_family_created", "family_members_active_created", "family_members_family_id_unique");
            }
        }
    }
}
