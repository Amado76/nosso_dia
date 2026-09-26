package com.beehome;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class TagMigrationTests {
    @Test void backfillsExistingDailyPlanItemsWithTheirFamily() {
        try (var postgres = new PostgreSQLContainer("postgres:18.3")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("14").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID family = UUID.randomUUID(), member = UUID.randomUUID(), plan = UUID.randomUUID(), item = UUID.randomUUID();
            jdbc.update("insert into beehome.families(id,name,timezone,created_at,updated_at) values (?, 'Family', 'UTC', now(), now())", family);
            jdbc.update("insert into beehome.family_members(id,family_id,name,member_type,active,created_at,updated_at) values (?,?, 'Child','CHILD',true,now(),now())", member, family);
            jdbc.update("insert into beehome.daily_plans(id,family_id,family_member_id,plan_date,created_at,updated_at) values (?,?,?,current_date,now(),now())", plan,family,member);
            jdbc.update("insert into beehome.daily_plan_items(id,daily_plan_id,title,sort_order,active,created_at,updated_at) values (?,?,'Existing',0,true,now(),now())",item,plan);
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("select family_id from beehome.daily_plan_items where id=?",UUID.class,item)).isEqualTo(family);
        }
    }
}
