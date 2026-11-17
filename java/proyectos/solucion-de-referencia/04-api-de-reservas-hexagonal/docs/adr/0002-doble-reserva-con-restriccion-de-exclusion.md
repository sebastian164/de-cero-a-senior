# ADR 0002: la garantía contra la doble reserva vive en la base de datos

**Estado:** aceptada.

**Contexto.** "Nunca dos reservas en la misma franja, tampoco con peticiones simultáneas." Comprobar en Java que no haya solape y luego insertar deja una ventana entre la comprobación y la inserción: dos peticiones pueden pasar la comprobación a la vez.

**Decisión.** Una restricción de exclusión de PostgreSQL (`V1__reservas.sql`): `EXCLUDE USING gist (espacio WITH =, tstzrange(inicio, fin) WITH &&) WHERE (estado = 'ACTIVA')`. La base rechaza el segundo solape de forma atómica; el repositorio traduce el error de la base a `Reserva.FranjaOcupada` y la API responde 409. Las reservas canceladas quedan fuera de la restricción, y las franjas contiguas (una termina cuando la otra empieza) no chocan porque el rango es semiabierto.

**Alternativas.** (1) Bloqueo pesimista (`SELECT ... FOR UPDATE`) sobre el espacio: serializa todas las reservas de un espacio y no cubre una fila que aún no existe. (2) Bloqueo optimista con una columna de versión: protege filas existentes, no la ausencia de un solape. (3) `synchronized` en Java: no sirve con más de una instancia de la aplicación. Se descartaron los tres: la restricción es correcta con cualquier número de instancias y no tiene ventana.

**Consecuencias.** Hace falta la extensión `btree_gist`. La regla "no hay solape" vive dos veces: como método (`Franja.seSolapaCon`, útil para el dominio y las pruebas) y como restricción (la que de verdad garantiza). La prueba de concurrencia lo demuestra: 20 hilos simultáneos, una reserva, 19 `FranjaOcupada`.
