# Solución de referencia: Seguridad con Spring Security

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Requisitos: JDK 21 y Maven 3.9 o superior.

## Ejecutarla y probarla

Desde esta carpeta:

```bash
mvn -B -ntp clean test
```

Con un Keycloak real se ejecutan además las pruebas de `KeycloakTest`:

```bash
docker compose -f docker-compose.keycloak.yml up -d   # espera a que responda en http://localhost:42180/realms/curso
mvn test -Dkc.url=http://localhost:42180
docker compose -f docker-compose.keycloak.yml down -v
```

## Qué se hizo y por qué

Las decisiones, los resultados medidos y los hallazgos están en [ANALISIS.md](ANALISIS.md).
