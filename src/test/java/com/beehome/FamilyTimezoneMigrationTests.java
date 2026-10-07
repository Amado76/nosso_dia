package com.beehome;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyTimezoneMigrationTests {
    @Test void removesTimezoneWithoutChangingExistingDatesTimesOrAuditInstants() {
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("20").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID family = UUID.randomUUID(), member = UUID.randomUUID(), plan = UUID.randomUUID(), item = UUID.randomUUID();
            jdbc.update("insert into beehome.families(id,name,timezone,created_at,updated_at) values (?, 'Family', 'America/Asuncion', '2026-09-21T01:00:00Z', '2026-09-21T02:00:00Z')", family);
            jdbc.update("insert into beehome.family_members(id,family_id,name,member_type,active,created_at,updated_at) values (?,?,'Child','CHILD',true,now(),now())", member, family);
            jdbc.update("insert into beehome.daily_plans(id,family_id,family_member_id,plan_date,created_at,updated_at) values (?,?,?,'2026-09-20',now(),now())", plan, family, member);
            jdbc.update("insert into beehome.daily_plan_items(id,daily_plan_id,family_id,title,scheduled_time,sort_order,active,created_at,updated_at) values (?,?,?,'Breakfast','08:30',0,true,now(),now())", item, plan, family);
            var before = jdbc.queryForMap("select id,name,created_at,updated_at from beehome.families where id=?", family);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForMap("select * from beehome.families where id=?", family)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select plan_date::text from beehome.daily_plans where id=?", String.class, plan)).isEqualTo("2026-09-20");
            assertThat(jdbc.queryForObject("select scheduled_time::text from beehome.daily_plan_items where id=?", String.class, item)).isEqualTo("08:30:00");
        }
    }
}
