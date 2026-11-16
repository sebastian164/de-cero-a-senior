# Solución de referencia — taller 07

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Cómo ejecutarla y probarla

Necesitas JDK 21, Maven y Docker (las pruebas levantan PostgreSQL 17 con Testcontainers). Compila el núcleo de la solución de la sesión 06 sin copiarlo, así que esa carpeta debe existir en el repositorio. Desde esta carpeta:

```bash
mvn -B clean package
```

Resultado esperado: **6 pruebas, 0 fallos**. Para arrancar la aplicación (puerto 8080) contra PostgreSQL:

```bash
docker compose up -d --wait
java -jar target/tienda-jpa-1.0.0.jar
docker compose down -v
```

Si el 8080 está ocupado: `SERVER_PORT=8081 java -jar target/tienda-jpa-1.0.0.jar`. Si el 5432 está ocupado, cambia el puerto en `compose.yaml` y en `application.yaml`.

Con la aplicación arrancada, comprueba que responde (devuelve 201):

```bash
curl -i -X POST localhost:8080/pedidos -H 'Content-Type: application/json' -H 'Idempotency-Key: k-1' -d '{"cliente":"ana","lineas":[{"producto":"teclado","cantidad":1}]}'
```

Limpia con `mvn clean`.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
