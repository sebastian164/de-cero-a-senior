# Kafka 05 — Eventos como contrato

Esquemas JSON en `esquemas/`, lectores en `src/main/java/contrato/` y `ContratoTest` (6 pruebas). El paso 10 se ejecutó contra Apicurio Registry 3.3.4 (imagen del `compose`), con la regla `COMPATIBILITY=BACKWARD`. Mediciones en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

| Paso | Resultado |
|---|---|
| 3. Validar | los tres eventos validan contra su esquema, y cada versión rechaza campos que no declara |
| 7. Productor v2, consumidor v1 | consumidor viejo (solo conoce v1): 20 bien, 0 fallos sobre 10 eventos v1 y 10 v2; consumidor nuevo (lee v2): 20 bien, 0 fallos, y asigna `web` al canal de un evento viejo |
| 8a. Renombrar `totalCentavos` a `total` de golpe | el consumidor viejo falla 5 de 5: `falta el campo totalCentavos` |
| 8b. Migración en dos fases | fase 1 (se publican los dos nombres): viejo 10/10 bien; fase 2 (solo `total`) tras migrar el consumidor: 5/5 bien, 0 fallos en todo el proceso |
| 9. Valor desconocido (`EUR`) | con enumeración estricta: `No enum constant ... Moneda.EUR`, el consumidor revienta; con lectura tolerante: `moneda=DESCONOCIDA`, se procesa y se marca para revisión |
| 10. Registro | v1: 200; v2 (campo opcional `canal`): 200; v3 (renombrar el campo): **400**, `Incompatible artifact` (rechazo completo guardado, al ejecutar, en `salida/rechazo-registro.txt`) |
| 11. Prueba de contrato | el consumidor v1 lee los tres eventos que el productor publica hoy; control negativo: con el productor renombrando el campo, la prueba falla con `falta el campo totalCentavos` |

**La regla de evolución del equipo (media página).**
1. Solo se añaden campos **opcionales** (con valor por defecto). Es compatible hacia atrás y hacia adelante en lectura.
2. Nunca se renombra, se elimina ni se cambia el tipo de un campo en la misma versión: se publica el campo nuevo junto al viejo, se migran los consumidores y entonces se retira el viejo (migración en dos fases, 8b).
3. Los consumidores son **tolerantes**: ignoran campos que no conocen y mapean valores de enumeración desconocidos a un valor "desconocido" que se marca, nunca revientan.
4. La compatibilidad se hace cumplir por máquina, no por memoria: regla `BACKWARD` en el registro y una prueba de contrato en la construcción del productor, con el control negativo comprobado.
5. Un cambio incompatible es un tema nuevo (`v2`) con los dos temas vivos durante la migración.
