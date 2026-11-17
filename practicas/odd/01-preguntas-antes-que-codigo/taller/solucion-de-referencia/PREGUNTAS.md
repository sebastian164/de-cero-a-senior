# Las diez preguntas (escritas antes de tocar el código)

Operación y negocio. Las tres últimas de operación son del tipo "¿por qué…?".

| # | Pregunta | Tipo |
|---|---|---|
| 1 | ¿Cuántas peticiones fallan por error del servidor (5xx), en proporción al total? | operación |
| 2 | ¿Cuántas peticiones rechaza el cliente (4xx) y por qué motivo (clave, nota inválida, método, ruta)? | operación |
| 3 | ¿Cuál es el p95 de duración de `POST /api/notas`, y el de `GET /api/notas`? | operación |
| 4 | ¿Cuántas notas se guardan por hora? | negocio |
| 5 | ¿Qué longitud tienen las notas que se guardan (media y máximo)? | negocio |
| 6 | ¿Cuántas veces se rechazó una nota por vacía o por demasiado larga? | negocio |
| 7 | ¿Por qué tardó tanto esta petición concreta? | operación (¿por qué?) |
| 8 | ¿Por qué hubo 503, y fue porque no estaba listo o por un fallo? | operación (¿por qué?) |
| 9 | ¿Por qué suben las notas lentas: qué tienen en común las peticiones lentas? | operación (¿por qué?) |
| 10 | Dado un identificador, ¿qué pasó con esa petición de punta a punta (en el nginx y en la API)? | operación |
