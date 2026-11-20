# Docker 03: análisis

Mediciones de `probar.sh` (Docker 29.9.3). Los tiempos y el uso de memoria varían un poco entre ejecuciones, por eso se dan como rangos de las últimas ejecuciones; los tamaños no varían.

| | 0.1.0 (sesión 02) | 1.0.0 |
|---|---|---|
| Tamaño | 746 MB | 74 MB |
| Usuario | `uid=0(root)` | `10001:10001` |
| Reconstrucción tras cambiar una línea | 0,6 a 0,9 s | 2,2 a 2,8 s (compila y vuelve a ejecutar `jlink`) |
| Memoria con `--memory 64m` | arranca, sin OOM, montón de 32 MiB | arranca, sin OOM, montón de 32 MiB, 28 a 31 MiB en uso de 64 MiB; la JVM 21 ya lee el límite del cgroup |
| `--read-only` | no se pidió | funciona con `DIRECTORIO_DATOS=/datos` en un volumen o `tmpfs`; `POST /api/notas` da 201 |
| Salud con arranque de 15 s | n/a | `starting` hasta unos 26 s y luego `healthy`, gracias a `--start-period=30s` |

## Decisión de imagen base

Medí dos bases para la etapa final: JRE de Temurin completo (476 MB) y Alpine con un entorno de ejecución reducido con `jlink` (74 MB); una tercera, distroless, la descarté por argumentos (sin medirla, ver el final del párrafo). Elegí Alpine + `jlink` con solo `java.base` y `jdk.httpserver`: 6,4 veces más pequeña, sin compilador, con `sh` y `wget` (necesarios para el `HEALTHCHECK`). Costo: la reconstrucción es más lenta (la etapa de `jlink`) y musl en lugar de glibc; para esta aplicación, sin código nativo, no importa. Una imagen sin distribución (distroless) habría sido aún más pequeña pero impide el `HEALTHCHECK` con `wget` y la depuración con `sh`.

## Criterios

- Sin compilador: `command -v javac` no devuelve nada.
- Usuario distinto de root: `uid=10001`.
- Detención en menos de 4 s con la forma exec.
