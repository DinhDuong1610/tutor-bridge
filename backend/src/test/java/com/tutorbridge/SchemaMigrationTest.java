package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchemaMigrationTest extends IntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Test
    void migrations_create_all_eighty_five_tables() {
        Integer tables = jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'tutorbridge' and table_type = 'BASE TABLE'
                """).query(Integer.class).single();

        assertThat(tables).isEqualTo(85);
    }

    @Test
    void application_connects_as_a_role_without_elevated_rights() {
        var role = jdbc.sql("""
                select current_user as name, rolsuper, rolcreaterole, rolcreatedb
                from pg_roles where rolname = current_user
                """).query().singleRow();

        assertThat(role.get("name")).isEqualTo("tutorbridge_app");
        assertThat(role.get("rolsuper")).isEqualTo(false);
        assertThat(role.get("rolcreaterole")).isEqualTo(false);
        assertThat(role.get("rolcreatedb")).isEqualTo(false);
    }

    @Test
    void application_role_can_write_data_and_fire_triggers() {
        UUID id = UUID.randomUUID();

        jdbc.sql("insert into accounts(id, role, status) values (?, 'PARENT', 'ACTIVE')").param(id).update();
        int updated = jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(id).update();
        int deleted = jdbc.sql("delete from accounts where id = ?").param(id).update();

        assertThat(updated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void application_role_cannot_change_the_schema() throws SQLException {
        try (Connection app = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            for (String statement : new String[] {
                "create table tutorbridge.intruder(id int)",
                "create table public.intruder(id int)",
                "alter table accounts add column intruder int",
                "drop table accounts",
                "truncate accounts",
                "select count(*) from public.flyway_schema_history",
            }) {
                assertThatThrownBy(() -> app.createStatement().execute(statement))
                        .as(statement)
                        .isInstanceOfSatisfying(SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
            }
        }
    }
}
