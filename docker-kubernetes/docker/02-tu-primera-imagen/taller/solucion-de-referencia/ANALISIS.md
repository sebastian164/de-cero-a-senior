# Docker 02: análisis

Ejecutado con Docker 29.9.3 y BuildKit 0.34. Las mediciones salen de `probar.sh`; los tiempos varían de una ejecución a otra.

## Caché de capas (paso 5)

| Cambio | Tiempo de reconstrucción | Capas reutilizadas |
|---|---|---|
| Ninguno | 272 ms | todas (3) |
| Una línea de `Servidor.java` | 235 ms | las anteriores a `COPY` del código; se rehace la compilación |
| Un comentario al final del Dockerfile | 294 ms | todas: el comentario no genera capa |

La regla: lo que cambia poco va arriba; lo que cambia siempre, abajo. Un cambio invalida su capa y todas las siguientes.

## Archivo inútil (paso 6)

Con `basura.bin` de 200 MB en el contexto: 1860 ms y «transferring context: 209.78 MB», imagen de 956 622 934 B. Con `.dockerignore`: 252 ms, «transferring context: 11.38 kB» y 746 699 387 B. El archivo no entra en ninguna imagen sin borrarlo.

## Señales (paso 7)

Forma shell (`CMD java ...`): PID 1 es `/bin/sh -c java -cp clases Servidor`; `docker stop` tarda 10 149 ms (espera el plazo y envía SIGKILL). Forma exec (`CMD ["java", ...]`): 155 ms. La causa: el shell no reenvía SIGTERM a su hijo.

## Web (paso 1)

Con `DESTINO_API=http://127.0.0.1:9` la página carga (nginx resuelve una IP al arrancar sin problema). Con un nombre inexistente nginx no arranca: `[emerg] host not found in upstream "no-existe-este-host"`. Lección: nginx resuelve el nombre del proxy al arrancar.

## Notas (paso 8)

Tras eliminar y recrear el contenedor, las notas se pierden: la sesión 04 lo resuelve con un volumen.

Imagen sencilla de la API: 746 699 387 B (JDK completo).
