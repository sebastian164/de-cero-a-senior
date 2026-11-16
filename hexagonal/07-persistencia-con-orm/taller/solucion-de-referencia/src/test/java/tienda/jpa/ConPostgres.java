package tienda.jpa;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL 17 real, en un puerto libre. */
abstract class ConPostgres {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("tienda").withUsername("banco").withPassword("banco");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        propiedades.add("logging.level.org.hibernate.SQL", () -> "info");
        propiedades.add("server.port", () -> "0");
    }
}
