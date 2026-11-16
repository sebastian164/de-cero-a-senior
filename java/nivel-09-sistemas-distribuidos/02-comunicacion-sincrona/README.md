# 02 — Comunicación síncrona: tiempos límite, reintentos e idempotencia

## Idea

Llamas a alguien por teléfono y no contesta. ¿Cuánto esperas? ¿Vuelves a llamar? ¿Cuántas veces? Y si la llamada se cortó justo cuando decías "sí, cómprelo", ¿la otra persona lo oyó? Estas preguntas, que resolvemos sin pensar entre personas, hay que programarlas entre servicios.

## Lo esencial

### Estilos

| Estilo | Características | Uso típico |
|---|---|---|
| REST sobre HTTP/JSON | Universal, legible, sin contrato estricto | APIs públicas y entre servicios |
| gRPC | Binario, contrato en Protobuf, *streaming*, rápido | Comunicación interna de alto volumen |
| GraphQL | El cliente pide exactamente lo que necesita | Agregación para interfaces |

En la comunicación síncrona, quien llama espera. Eso crea **acoplamiento temporal**: si el destino está caído o lento, quien llama también lo está.

### Tiempos límite

Toda llamada remota necesita un tiempo límite de conexión y de respuesta. Sin él, un servicio lento retiene hilos y conexiones de quien lo llama hasta agotarlos: el fallo se propaga en cascada. El límite debe ser menor que el de quien espera aguas arriba (**presupuesto de tiempo**).

### Reintentos

Solo tienen sentido ante fallos **transitorios** (tiempo vencido, 503, error de red) y nunca ante errores del cliente (400, 404, 422). Reglas:

- **Retroceso exponencial:** esperar 100 ms, 200 ms, 400 ms…
- **Variación aleatoria (*jitter*):** para que mil clientes no reintenten en el mismo instante.
- **Máximo de intentos** y presupuesto total de tiempo.
- **No multiplicar:** si tres capas reintentan tres veces, el servicio del fondo recibe 27 peticiones. Reintenta en una sola capa.

### Idempotencia

Una operación es idempotente si ejecutarla varias veces tiene el mismo efecto que ejecutarla una. Como los reintentos son inevitables, **toda operación que se reintente debe ser idempotente**.

| Naturalmente idempotente | No idempotente |
|---|---|
| `GET`, `PUT`, `DELETE`; "fijar el estado en X" | `POST` de creación; "sumar 10"; "cobrar" |

**Clave de idempotencia:** el cliente genera un identificador único por operación y lo envía. El servidor guarda la clave con el resultado; si la clave llega de nuevo, devuelve el resultado guardado sin repetir la operación.

## Ejemplo

```java
@Transactional
public ResultadoPago cobrar(String claveIdempotencia, CobroComando comando) {
    return cobros.buscarPorClave(claveIdempotencia)
            .map(Cobro::resultado)                                   // ya se procesó: mismo resultado
            .orElseGet(() -> {
                ResultadoPago resultado = pasarela.cobrar(comando);
                cobros.guardar(new Cobro(claveIdempotencia, resultado));   // restricción única en la clave
                return resultado;
            });
}
```

La restricción única sobre la clave evita que se guarden dos resultados, pero **no** evita el doble cobro con dos peticiones simultáneas: ambas pasan la búsqueda, ambas llaman a la pasarela y solo la segunda falla al guardar, cuando el cobro ya se hizo (verificado con PostgreSQL 18). Para ese caso, reserva la clave **antes** del efecto (`INSERT ... ON CONFLICT (clave) DO NOTHING` en la misma transacción): la segunda petición espera en el índice único y luego lee el resultado guardado. Si el efecto es una llamada remota (la pasarela), envíale también la misma clave: así cubres la caída entre el cobro y el *commit*, que revertiría la reserva y dejaría que el reintento cobre otra vez.

Cliente con tiempos límite y reintento (Resilience4j):

```java
RestClient cliente = RestClient.builder()
        .requestFactory(conTiemposLimite(Duration.ofMillis(500), Duration.ofSeconds(2)))
        .build();

@Retry(name = "inventario")                // 3 intentos, retroceso exponencial con variación
public Existencias consultar(Long productoId) { ... }
```

## Errores comunes

- Llamadas sin tiempo límite (el valor por defecto de muchos clientes es infinito).
- Reintentar operaciones no idempotentes: cobros duplicados.
- Reintentos inmediatos y sin tope: una avalancha sobre un servicio que intentaba recuperarse.
- Reintentar errores 4xx.
- Cadenas largas de llamadas síncronas: la latencia se suma y la disponibilidad se multiplica a la baja.
- Tratar un tiempo vencido como "no ocurrió".
- Llamadas remotas dentro de una transacción de base de datos.

## Taller

1. Crea dos servicios. El segundo responde lento al azar. Demuestra cómo, sin tiempo límite, el primero agota sus hilos.
2. Agrega tiempos límite y reintentos con retroceso exponencial y variación.
3. Haz que el segundo servicio procese la petición pero la respuesta se pierda. Demuestra el efecto duplicado del reintento.
4. Implementa claves de idempotencia y demuestra que el duplicado desaparece, también con dos peticiones simultáneas.
5. Calcula el presupuesto de tiempo de una cadena de tres servicios cuyo cliente final espera como máximo dos segundos.

Rama `taller/n9-02-sincrona`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué toda llamada remota necesita tiempo límite?
2. ¿Qué es la idempotencia y cómo la implementas en un `POST`?
3. ¿Qué es una tormenta de reintentos y cómo se evita?
4. ¿Qué errores se reintentan y cuáles no?
5. REST frente a gRPC: criterios.
6. Un cliente recibe un tiempo vencido al pagar. ¿Qué debe hacer?
