# Solución de referencia: Observabilidad y preparación para producción

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Requisitos: JDK 21 y Maven 3.9 o superior.

## Ejecutarla y probarla

Desde esta carpeta:

```bash
mvn -B -ntp clean test
```

El escenario completo (Prometheus, Grafana y Tempo en Docker, dos servicios y diagnóstico por métricas y trazas) se ejecuta con:

```bash
mvn package -DskipTests
./escenario.sh
```

La salida queda en `target/escenario/`.

## Qué se hizo y por qué

Las decisiones, los resultados medidos y los hallazgos están en [ANALISIS.md](ANALISIS.md).
