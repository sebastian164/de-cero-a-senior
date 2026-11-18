# Taller 11-04: notificaciones

Código: `src/main/java/notificaciones/` (Java 21, sin frameworks). Las pruebas están en `src/test/java/notificaciones/` y se ejecutan con `./mvnw clean test` (ver `README.md`).

## 1. Núcleo

- **API:** `ServicioDeNotificaciones.enviar(clave, canal, prioridad, destino)`: en producción sería `POST /v1/notificaciones` con `Idempotency-Key`.
- **Modelo:** `Notificacion(id, claveIdempotencia, canal, prioridad, destino)`; canales EMAIL, SMS, PUSH.
- **Colas por canal y prioridad:** un `ColaConArrendamiento` por canal (`Map<Canal, ColaConArrendamiento>`), en memoria, con dos niveles de prioridad (siempre sale primero lo ALTO), retraso para reintentos y **arrendamiento**: un mensaje entregado a un trabajador que no confirma a tiempo vuelve a la cola (como la visibilidad de SQS). Es al menos una vez.
- **Trabajadores por canal:** `trabajador(canal, ...)` solo consume la cola de su canal. Un canal lento no retrasa a los demás (sección 3b).
- **Puerto `CanalDeEnvio`** (Adapter/Strategy): el servicio solo conoce `nombre()` y `enviar(n)`. `Proveedor` es el adaptador simulado (latencia fija, fallos al azar, interruptor para "tumbarlo"); `CanalConLimite` es un decorador con una **cubeta de fichas** (`LimitadorDeCubeta`) que impone la cuota del proveedor (sección 3c).

## 2. Idempotencia, reintentos con retroceso y cola de mensajes fallidos

- Idempotencia: la misma clave solo se encola una vez (`laIdempotenciaEvitaEncolarDosVeces`: tres envíos, una entrega).
- Reintentos: retroceso exponencial (`base · 2^(intento-1)`) con *jitter*, hasta 5 intentos.
- Cola de fallidos (DLQ): tras el quinto intento, el mensaje va a `muertas()`. Medido con un proveedor con 30 % de fallo al azar: 2.000 enviadas → 1.999 entregadas, ~800 reintentos, 1 en la cola de fallidas (el 0,24 % esperado). Con un proveedor siempre caído y sin respaldo: las 100 van a la cola de fallidas tras exactamente 4 reintentos cada una.

## 3. Campaña de 100.000 y prioridad alta

Salida medida (66 trabajadores, 22 por canal, 1 ms por envío, 5 % de fallo del primario; 300 códigos de acceso de ALTA prioridad llegan durante la campaña):

| Prioridad | p50 | p99 |
|---|---|---|
| ALTA (300) | 1 ms | 2 a 6 ms |
| NORMAL (100.000) | ≈ 1.000 ms | 2.100 a 3.700 ms |

La prueba exige que el p99 de ALTA sea cinco veces menor que la mediana de NORMAL y menor de 250 ms: **las altas no se retrasan** porque nunca esperan detrás de la campaña (cola separada, despacho preferente). Compromiso: una avalancha de ALTA dejaría sin servicio a NORMAL (inanición); se limita con una cuota (por ejemplo, máximo 50 % de los trabajadores para ALTA).

### 3b. Aislamiento por canal

Prueba `unProveedorLentoNoRetrasaLosOtrosCanales`: 8 trabajadores por canal, 400 SMS con un proveedor de 50 ms (2,5 s de trabajo) y 50 correos de 1 ms encolados detrás. Los 50 correos se entregan con **p99 de EMAIL de ~8 ms mientras la cola de SMS sigue ocupada**. Con la versión anterior (una sola cola y un solo grupo de 8 trabajadores) la misma situación con 1.000 SMS dejaba los correos esperando 6,8 s: justo el acoplamiento que el aislamiento por canal evita.

### 3c. Límite de tasa por proveedor

Los proveedores reales conceden una cuota (por ejemplo, 500 SMS/s) y devuelven 429 si se supera; la estimación del método (≈2.800/s en una campaña) obliga a repartir y a frenar. `elLimitadorNoSuperaLaCuotaDelProveedor`: cuota de 500/s con ráfaga de 50, 16 trabajadores que sin freno enviarían miles por segundo, 1.500 envíos: tardan ~2,9 s (≈515/s) y **ninguna ventana de 1 s supera 549 envíos** (cuota + ráfaga). El límite es del proveedor, no del trabajador: por eso va en el decorador del canal y se comparte entre todos los trabajadores.

## 4. Caída de un proveedor y conmutación

Con el primario de SMS caído y 3.000 mensajes: **3.000 entregados por el respaldo, 0 fallidos**, y el primario recibió solo 16 intentos gracias al cortacircuitos (5 fallos seguidos lo abren 300 ms; sin él serían 3.000 intentos). El cortacircuitos tiene tres estados: **cerrado** (usa el primario y cuenta fallos), **abierto** (va directo al respaldo durante 300 ms) y **semiabierto** (vencido el plazo, un solo envío de prueba al primario elegido con una operación atómica; si sale bien se cierra, si falla se reabre). `elCortacircuitosSemiabiertoCierraCuandoElPrimarioSeRecupera`: con el primario caído recibe solo 8 de 200 intentos; al recuperarse, la sonda lo cierra y vuelve a recibir casi todo el tráfico. Compromiso: el respaldo suele costar más o tener otra latencia.

## 5. Matar un trabajador a mitad del envío

Se hace morir a 8 trabajadores justo después de enviar al proveedor y antes de confirmar. Resultado: **1.000 de 1.000 notificaciones llegan (no se pierde nada)** y **8 duplicados**: cada mensaje en vuelo al morir su trabajador vuelve a la cola al vencer el arrendamiento (150 ms) y se reenvía una vez. Con un proveedor que **no** respeta la clave de idempotencia, el usuario ve 1.008 entregas; con uno que la respeta (por ejemplo, usando la clave como identificador del mensaje), ve 1.000. Es la garantía honesta de "al menos una vez": para evitar el duplicado visible hay que pasar la clave de idempotencia al proveedor.

## 6. Documento de diseño (método de seis pasos)

**1. Requisitos.** Enviar notificaciones por email, SMS y *push*, con plantillas, preferencias del usuario y prioridades (códigos de acceso > transaccionales > campañas). Fuera: diseño de plantillas, analítica. No funcional: no perder notificaciones; los códigos de acceso llegan en segundos aun durante campañas; tolerar la caída de un proveedor; no enviar dos veces cuando se pueda evitar.

**2. Estimaciones.** Campaña de 10 millones en 1 hora: 10⁷ ÷ 3.600 ≈ 2.800/s; el flujo normal, 1 millón/día ≈ 12/s. Con 1 ms por envío y 64 trabajadores medidos: ≈ 60.000/s por nodo (límite de la simulación, no del proveedor real); con proveedores reales de 100 ms por llamada, 2.800/s requieren ≈ 280 conexiones concurrentes. Almacenamiento: 10 M × 300 B = 3 GB por campaña.

**3. API.** `POST /v1/notificaciones {canal, destino, plantilla, parámetros, prioridad}` con `Idempotency-Key` → `202 {id}`; `GET /v1/notificaciones/{id}` → estado (EN_COLA, ENVIADA, FALLIDA).

**4. Datos.** `notificaciones(id, clave UNIQUE, canal, prioridad, estado, intentos, creada_en)`; cola durable (Kafka o SQS) con una cola por prioridad y canal; DLQ.

**5. Alto nivel.** API → idempotencia (base) → colas por prioridad → trabajadores por canal → proveedor primario/respaldo (cortacircuitos) → estado.

**6. Decisiones.**
| Decisión | Alternativa | Costo |
|---|---|---|
| Al menos una vez + idempotencia hacia el proveedor | Exactamente una vez (inalcanzable de extremo a extremo) | Duplicados posibles si el proveedor no deduplica (medido: 8 de 1.000 al matar 8 trabajadores) |
| Colas separadas por prioridad | Una cola con prioridad por mensaje | Más colas que operar; evita la inanición con cuotas |
| Cortacircuitos con proveedor de respaldo | Reintentar siempre al primario | Costo y latencia del respaldo; lógica de apertura/cierre del circuito |
