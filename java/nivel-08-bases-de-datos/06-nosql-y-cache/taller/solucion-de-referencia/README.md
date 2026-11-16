# Solución de referencia del taller

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

## Cómo ejecutarla

Requiere JDK 21 y Maven.

Necesita Docker en marcha para PostgreSQL, Redis y MongoDB:

```bash
docker compose up -d --wait
```

```bash
mvn verify
```

Ese comando compila y ejecuta todas las pruebas de esta carpeta.

Al terminar: `docker compose down -v`.

Levanta una solución a la vez: todas usan los mismos puertos (43xxx). Baja la anterior con `docker compose down -v` antes de pasar a otra.

## Qué hay aquí

- `src/main`: la solución. `src/test`: las pruebas que la comprueban.
- [ANALISIS.md](ANALISIS.md): el razonamiento, las decisiones y las cifras que obtuvo la solución.

Tu propia entrega va en `../solucion/`, no aquí.
