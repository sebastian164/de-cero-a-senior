# Kafka 03 — Consumidores y grupos, con medidas

Código en `src/main/java/consumidores/Consumidor.java` y pruebas en `ConsumidoresTest` (9 pruebas contra el broker real; sus mediciones, en `salida/evidencia.txt`, que escriben al ejecutarse).

| Paso | Qué se midió (última ejecución completa) |
|---|---|
| 2. Reparto | con 1, 2, 3 y 4 consumidores del mismo grupo las particiones por miembro fueron `[3]`, `[1, 2]`, `[1, 1, 1]`, `[0, 1, 1, 1]`: el cuarto se queda sin nada, porque una partición solo se asigna a un miembro del grupo |
| 3. Caída | un consumidor muerto con `SIGKILL` tardó unos 45 s en perder sus particiones (el `session.timeout.ms` por defecto es 45 s); con apagado ordenado, unos 2,5 s: cierra y avisa de que se va |
| 4. Otro grupo | con `earliest` recibe el historial (5) y lo nuevo (1); con `latest`, solo lo nuevo |
| 5. Confirmar después | producidos 10, procesados 11 (el mensaje que rompió se procesó dos veces): **repetición**, y el retraso final del grupo es 0 |
| 6. Confirmar antes | producidos 10, procesados 9: **pérdida** de un mensaje (cero veces procesado) y el retraso final del grupo también es 0: el retraso no delata la pérdida |
| 7. Autoconfirmación + hilos | 200 producidos, 20 procesados, retraso 0: se perdieron 180 con el proceso muerto y el retraso sigue en verde |
| 8. Más lento que `max.poll.interval.ms` | ingenuo: el mensaje lento se entregó 4 veces, 3 reequilibrios y 4 confirmaciones rechazadas; solución A (subir `max.poll.interval.ms`) y B (trabajador aparte con `pause()`/`resume()`): una entrega, cero reequilibrios |
| 9. Ponerse al día | con el productor a 100 mensajes/s: 1 consumidor 14 s, 2 consumidores 6 s, 3 consumidores 2 s (retraso inicial ~1000 mensajes: versión reducida de los cinco minutos del enunciado; la proporción entre 1, 2 y 3 consumidores es la misma) |
| 10. Reiniciar el grupo | con el consumidor activo la herramienta rechaza: `Assignments can only be reset if the group ... is inactive`; con el grupo detenido, la posición vuelve a 0 y al arrancar se reprocesaron 4 mensajes |

**Paso 8, costo de cada solución.** A es una línea de configuración, pero solo mueve el límite: si el mensaje tarda más, vuelve el problema, y un consumidor realmente colgado tarda más en detectarse. B resuelve el caso en serio (el hilo del `poll` sigue vivo), pero cuesta complejidad: hay que pausar, guardar el resultado de otro hilo, reanudar y confirmar con cuidado desde el hilo correcto.

**Paso 10.** La herramienta exige que el grupo esté **inactivo**. En producción se usa para reprocesar tras un error de lógica, y solo es seguro si el consumidor es idempotente (se reprocesan mensajes ya aplicados).

**¿Qué garantía de entrega ofrece el consumidor final y por qué?** *Al menos una vez*: procesa y después confirma la posición (paso 5), así que un fallo entre las dos acciones repite el mensaje y nunca lo pierde. Pasar a *exactamente un efecto* exige idempotencia en el destino (sesión 04). Confirmar antes (paso 6) da *como mucho una vez* y pierde datos sin ninguna señal en el retraso.
