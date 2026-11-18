# Taller 11-03: acortador de URLs

Código: esta misma carpeta (Spring Boot 4.1.2, Java 21, PostgreSQL, Caffeine). Se ejecuta con `./taller.sh`: pruebas automáticas contra PostgreSQL real y carga con k6 (ver `README.md`).

## 1. Servicio

`POST /v1/urls` (alias, expiración, validación: 400 si la URL no es http/https o el alias no cumple `[A-Za-z0-9_-]{3,16}`; 409 si el alias está ocupado), `GET /{codigo}` → 302 o 404, `GET /v1/urls/{codigo}/estadisticas`. La redirección usa 302 para poder contar clics (con 301 el navegador ya no vuelve). La expiración se aplica al leer (el servicio devuelve 404 y borra la fila: eliminación perezosa; un proceso periódico de limpieza queda como mejora).

## 2. Dos estrategias de generación, comparadas (salida de `ComparacionDeEstrategiasTest`)

| Estrategia | Longitud | Colisiones en 5 millones de códigos | Caracteres distintos entre dos consecutivos (de 7) |
|---|---|---|---|
| Contador en base 62 | 7 | 0 (único por construcción) | 1,02 (totalmente predecible) |
| Rangos de contador + permutación afín (la del README) | 7 | 0 (único por construcción) | 5,06 |
| **Rangos de contador + permutación con clave (Feistel; la que usa el servicio)** | 7 | 0 (único por construcción) | 6,89 |
| Aleatorio con comprobación | 7 | 1 a 3 (esperadas ≈ 3,5 por la paradoja del cumpleaños; la base las detecta y se reintenta) | 6,89 |

**Hallazgo:** la permutación del README (`n·A + B mod 62⁷`, afín) esconde la adyacencia a simple vista pero es **lineal**: dos códigos consecutivos revelan el paso, y de ahí todos los demás (`laPermutacionAfinEsPredecibleSiSeConoceUnPar`). Cumple "no adivinables en secuencia" frente a quien enumera, no frente a quien analiza. **Corrección implementada:** `GeneradorPorRangos` ya no usa la afín sino `PermutacionConClave`: una red de Feistel de 6 rondas sobre 42 bits (2⁴² > 62⁷) con HMAC-SHA256 como función de ronda y recorrido de ciclo para quedarse dentro de [0, 62⁷) (en promedio 1,25 cifrados por código). La clave viene de la configuración (`CLAVE_CODIGOS`) y **no tiene valor por defecto**: sin ella la aplicación no arranca (para trabajar en local existe el perfil `desarrollo`, que usa una clave pública y no debe llegar a producción; `AcortadorSinClaveTest` lo comprueba). Las pruebas comprueban la **biyección** (200.000 entradas sin colisión, ida y vuelta, rechazo de valores fuera del espacio) y que **el ataque lineal que predice el 100 % con la afín (1.000 de 1.000) acierta 0 de 1.000 con la clave**; la afín se conserva solo como contraejemplo (`Base62.AFIN`). Al agotarse el espacio (bloque × tamaño ≥ 62⁷) se lanza `IllegalStateException` en vez de repetir códigos. Recomendación: rangos con permutación con clave (rendimiento sin lectura extra y códigos no adivinables sin conocer la clave) y, si el secreto no puede custodiarse, aleatorio con comprobación.

## 3. Caché bajo carga (k6, 32 usuarios virtuales, 100.000 enlaces, sesgo hacia pocos populares)

| | Redirecciones/s | Mediana (ms) | p95 (ms) |
|---|---|---|---|
| Sin caché | 4.454 | 5,99 | 14,39 |
| Con caché (Caffeine) | 7.996 | 1,19 | 13,91 |

La caché mejora el caso típico (mediana 5 veces menor) más que el p95, porque el p95 lo dominan los fallos de caché y la pausa de otros procesos en una máquina compartida. Con 0 errores en ambas.

## 4. Clics asíncronos

La redirección solo incrementa un contador en memoria; un proceso agrupa y escribe cada 500 ms (una escritura por código). La prueba `losClicsSeCuentanDeFormaAsincrona` hace 25 visitas y comprueba que la base acaba en 25 sin escribir durante la redirección. **Concurrencia:** una primera versión con `LongAdder` (`computeIfAbsent(...).increment()` y `remove()` + `sumThenReset()`) perdía clics si un hilo incrementaba un contador ya retirado (se midieron 327.688 perdidos de 16.000.000 con el patrón aislado). Ahora el mapa es `ConcurrentHashMap<String, Long>` con `merge(codigo, 1, Long::sum)` al registrar y `remove(codigo)` al volcar, ambos atómicos por clave, y si el UPDATE falla lo retirado se devuelve con `merge`. La prueba `elVolcadoConcurrenteNoPierdeClics` registra 8 × 50.000 clics mientras otro hilo vuelca sin parar y exige 400.000 exactos en la base. **Compromiso:** si la instancia cae, se pierden como máximo los clics de los últimos 500 ms; si no es aceptable, se publica un evento a una cola durable (Kafka) y se consume aparte.

## 5. Documento de diseño con el método de seis pasos

**1. Requisitos.** Crear, redirigir, expirar, alias. Fuera: cuentas, moderación. No funcional: redirección en decenas de ms, alta disponibilidad, un enlace creado no se pierde, códigos no secuenciales.

**2. Estimaciones propias.** Supuesto: 30 millones de URLs nuevas al mes (menor que el ejemplo del README para que el ejercicio no sea una copia): 30.000.000 ÷ 2.592.000 s ≈ **12 escrituras/s**. Proporción 100:1 → ≈ 1.200 lecturas/s; pico ×5 = 6.000/s. Cinco años: 1.800 millones de registros × 500 B = **0,9 TB**. Espacio de códigos: 62⁷ = 3,5·10¹² contra 1,8·10⁹ registros: 0,05 % de ocupación (la probabilidad de colisión del aleatorio, ≈ 0,05 %, es manejable). Capacidad medida: una instancia sirve ~4.500 redirecciones/s sin caché y ~8.000 con caché en esta máquina, así que **una instancia con caché cubre el pico previsto de 6.000/s**; se añaden dos instancias por disponibilidad, no por capacidad.

**3. API.** La de `ControladorUrls` (arriba).

**4. Datos.** `urls(codigo PK, url_larga, creado_en, expira_en, clics)`; secuencia `rangos` para repartir bloques.

**5. Alto nivel.** Cliente → balanceador → instancias sin estado (caché local) → PostgreSQL (primaria + réplica de lectura). Contador de clics en memoria por instancia con volcado cada 500 ms.

**6. Decisiones y compromisos.**
| Decisión | Alternativa | Costo |
|---|---|---|
| Rangos + permutación con clave (opcional aleatorio) | Aleatorio siempre; afín (predecible, medido) | Un secreto que custodiar y rotar (rotarlo cambia los códigos futuros, no los ya emitidos) |
| Caché local por instancia (más caché negativa de 30 s para los "no existe") | Caché distribuida | Un alias recién creado puede dar 404 hasta 30 s en las otras instancias (caché negativa); (la caché negativa solo se invalida en la instancia que crea el alias: la vida corta de 30 s es el compromiso; la alternativa es invalidar por publicación y suscripción) un enlace borrado/expirado puede servirse hasta 10 minutos por instancia; se valida `expira_en` en cada acierto de caché (hecho) |
| Clics en memoria con volcado | Evento a cola durable | Pérdida de hasta 500 ms de clics al caer |
| 302 | 301 | Más carga en el servicio; sí hay estadísticas |

## 6. ¿Y si las URLs pudieran editarse?

- **Caché:** pasa a haber invalidación. Con caché local por instancia no se puede invalidar en las demás: hace falta un canal de invalidación (pub/sub) o un tiempo de vida corto; o caché distribuida con borrado explícito.
- **301 deja de ser opción:** el navegador guardaría el destino viejo.
- **Modelo:** una URL deja de ser inmutable; se necesita historial (auditoría) y control de quién puede editar (propietario): aparece `usuario_id` y autorización.
- **Seguridad:** una edición puede convertir un enlace inocente en uno malicioso después de haberse difundido; se necesita revalidación del destino y listas de bloqueo en cada edición.
- **Estadísticas:** los clics se atribuyen a una versión del destino.
