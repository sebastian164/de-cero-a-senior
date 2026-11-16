# El banco, con JPA

El mismo núcleo de [`../proyecto/`](../proyecto/) con otros adaptadores: Spring Boot, JPA con Hibernate y PostgreSQL. Se explica en la [sesión 07](../07-persistencia-con-orm/).

> Compilado y ejecutado con Spring Boot 4.1.1 (Hibernate 7.4.5), JDK 21, Maven 3.10 y PostgreSQL 17: las órdenes de abajo, el contrato del repositorio de la sesión 08 y transferencias cruzadas desde 8 hilos. Si usas otra versión de Spring Boot, ajústala en el `pom.xml`.

El dominio y la aplicación no se copian: el `pom.xml` compila `../proyecto/src` y deja fuera sus adaptadores y su `Main`.

```bash
docker compose up -d
mvn spring-boot:run
```

```bash
curl -s -X POST localhost:8080/cuentas -H 'Content-Type: application/json' -d '{"titular":"Ana","centavos":100000000}'
```

Para ver los datos:

```bash
docker compose exec postgres psql -U banco -d banco -c 'select id, titular, saldo_centavos, version from cuenta'
```
