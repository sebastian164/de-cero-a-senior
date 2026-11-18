# Taller 11-01: método de seis pasos (tres sistemas)

Las cifras salen de `estimaciones.py` (`python3 estimaciones.py`; termina con `OK` si sus comprobaciones pasan). Los supuestos se dicen en voz alta en cada sistema.

---
## Sistema 1: turnos para una entidad pública con 300 sedes

**1. Requisitos.** Funcionales: sacar turno (en el kiosco, en la web o por celular), llamar el turno desde la ventanilla, ver la pantalla de la sala, cancelar. *Fuera:* citas con semanas de anticipación, pagos, biometría. No funcionales: cada sede debe seguir atendiendo si cae la conexión con el centro (disponibilidad local); un turno nunca se entrega dos veces; la pantalla se actualiza en menos de 5 s; datos personales mínimos.

**2. Estimación.** Supuesto: 400 turnos por sede y día. 300 × 400 = 120.000 turnos/día; en una jornada de 8 h, 120.000 ÷ 28.800 s ≈ 4,2/s, pico ×5 ≈ 21/s. Pantallas: 300 sedes × 10 pantallas ÷ 5 s = 600 lecturas/s. Almacenamiento: 120.000 × 250 días × 200 B = 6 GB/año. **Conclusión:** escala pequeña; el problema es la disponibilidad por sede, no el volumen.

**3. API.**
```
POST /v1/sedes/{s}/turnos          {servicio}        → 201 {turno:"A-0042", posicion}
POST /v1/ventanillas/{v}/siguiente                   → 200 {turno} | 204 sin turnos
GET  /v1/sedes/{s}/pantalla                          → turnos en atención y siguientes (cacheable 2 s)
DELETE /v1/turnos/{id}
```
**4. Datos.** `turnos(sede_id, servicio, numero, estado, creado_en)` con clave `(sede_id, servicio, numero)`; el número sale de un contador **por sede y servicio** (`UPDATE contadores ... RETURNING`). Acceso por sede: la partición natural es la sede.

**5. Diagrama.**
```
Kiosco/Web/App → balanceador → API de turnos → PostgreSQL (primaria + réplica)
                                    │
Sede: nodo local (turnos de emergencia) ⇄ sincroniza con el centro cuando hay red
Pantallas ← caché de 2 s ← API
```
**6. Tres decisiones.**
| Decisión | Alternativas | Costo |
|---|---|---|
| Contador transaccional por sede/servicio en la base | UUID sin orden (no se puede "llamar el 42"); contador en Redis (otro componente que puede perder el valor) | Un punto de serialización por sede: aceptable a 21 turnos/s |
| Modo degradado: un nodo local por sede con su propio rango de números | Exigir conexión siempre (la sede se detiene cuando cae el enlace) | Reconciliar al volver el enlace; prefijos de rango por sede evitan conflictos |
| Pantallas con consulta periódica y caché de 2 s | WebSocket/SSE (menos latencia, más conexiones y estado) | Hasta 2 s de retraso; sencillo de operar |

---
## Sistema 2: plataforma de encuestas con pico de respuestas en una hora

**1. Requisitos.** Funcionales: crear encuesta, publicar enlace, responder (una vez por persona), ver resultados agregados. *Fuera:* análisis estadístico avanzado, encuestas con lógica ramificada. No funcionales: no perder respuestas en el pico; los resultados pueden tener minutos de retraso; durabilidad alta; una respuesta por persona.

**2. Estimación.** Supuesto: 2 millones de respuestas en una hora. 2.000.000 ÷ 3.600 ≈ 556/s promedio; el primer cuarto de hora concentra: pico ×4 ≈ 2.222/s. Con 2 KB por respuesta, 4,4 MB/s de ingreso y 4 GB en total. Consumidores que insertan por lotes a 500/s: 4 absorben el pico.

**3. API.**
```
POST /v1/encuestas/{id}/respuestas  (Idempotency-Key)  {respuestas[]} → 202 {recibida:true}
GET  /v1/encuestas/{id}/resultados                      → agregados (con marca de tiempo)
```
**4. Datos.** `respuestas(encuesta_id, respondiente_id UNIQUE por encuesta, enviada_en, cuerpo jsonb)`, `agregados(encuesta_id, pregunta_id, opcion, conteo)`.

**5. Diagrama.**
```
Cliente → CDN (página estática) → API (valida, responde 202) → Cola durable → consumidores (inserción por lotes) → PostgreSQL
                                                                     └→ actualizador de agregados → caché de resultados
```
**6. Tres decisiones.**
| Decisión | Alternativas | Costo |
|---|---|---|
| Aceptar con 202 y escribir tras una cola | Escribir en la base dentro de la petición (el pico la ahoga) | Consistencia eventual: el respondiente no ve su respuesta en los resultados al instante |
| Idempotencia por `(encuesta, respondiente)` y clave única | Confiar en que el cliente no reintente | Una restricción única más en la base y una clave generada en el cliente |
| Resultados por agregados incrementales | Contar con `GROUP BY` al consultar (se hunde con millones de filas) | Los agregados pueden desfasarse de las filas; hay un recálculo periódico para corregir |

---
## Sistema 3: subida y consulta de documentos con control de acceso

**1. Requisitos.** Funcionales: subir un documento, descargarlo, compartirlo con personas o grupos, listar los propios. *Fuera:* edición colaborativa, búsqueda de texto completo. No funcionales: nadie lee un documento sin permiso (seguridad por encima de la velocidad); durabilidad muy alta; auditoría de accesos; archivos hasta 100 MB.

**2. Estimación.** Supuesto: 50.000 usuarios, 20 documentos al mes de 2 MB. 1 millón de subidas/mes, **24 TB/año**; lecturas: 10 por documento y mes ≈ 3,9/s promedio, 77 MB/s a pico ×10. **Conclusión:** el costo es almacenamiento y ancho de banda, no peticiones; los archivos no pasan por la API.

**3. API.**
```
POST /v1/documentos {nombre, tamaño, tipo} → 201 {id, urlDeSubida (prefirmada, 15 min)}
GET  /v1/documentos/{id}                    → 200 {metadatos, urlDeDescarga prefirmada, 5 min} | 403 | 404
PUT  /v1/documentos/{id}/permisos           {principal, rol}
```
**4. Datos.** `documentos(id, propietario, ruta, tamaño, sha256, estado)`, `permisos(documento_id, principal, rol)`, `auditoria(...)` solo de inserción. Los bytes en almacenamiento de objetos.

**5. Diagrama.**
```
Cliente → API (autenticación, autorización contra permisos) ──► URL prefirmada
Cliente ═══════════════════ sube/baja directo ═══════════════► Almacenamiento de objetos (cifrado en reposo)
API → PostgreSQL (metadatos y permisos) → auditoría
```
**6. Tres decisiones.**
| Decisión | Alternativas | Costo |
|---|---|---|
| URLs prefirmadas de vida corta | Pasar los bytes por la API (cuello de botella de 77 MB/s) | Una URL filtrada sirve hasta que vence; se acorta la vida |
| Autorización en la API con permisos en la base | Permisos en el almacenamiento de objetos (granularidad pobre) | Cada descarga consulta la base (cacheable por usuario, con invalidación al cambiar permisos) |
| Documento visible solo tras verificar tamaño y huella | Visible al instante (puede quedar a medias) | Un estado `PENDIENTE` y un proceso que lo confirma |
