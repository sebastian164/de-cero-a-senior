# El banco, con JPA

El mismo núcleo de [`../proyecto/`](../proyecto/) con otros adaptadores: Spring Boot, JPA con Hibernate y PostgreSQL. Se explica en la [sesión 07](../07-persistencia-con-orm/).

> Este proyecto **no se compiló ni se ejecutó** contra las bibliotecas reales al escribir el curso. Constrúyelo en tu máquina; comprueba antes la versión estable de Spring Boot y ajústala en el `pom.xml`.

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
