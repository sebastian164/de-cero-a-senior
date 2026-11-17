# 02 — La configuración que importa

Los valores por defecto de esta sesión son los de HikariCP 7, la serie vigente al escribir. Algunos cambiaron entre versiones (en especial `keepaliveTime`): confirma los de la tuya en su documentación.

## Idea

Las cajas de un supermercado. Con veinte clientes, abrir veinte cajas parece lo más rápido. Pero si detrás hay una sola persona empacando y un solo datáfono compartido, las veinte cajeras se estorban esperando el mismo recurso, y el conjunto va más lento que con cuatro cajas y una fila ordenada. El cuello de botella no era el número de cajas. En una base de datos, ese recurso compartido son los procesadores y el disco del servidor.

## Lo esencial

### Las propiedades que de verdad se tocan

| Propiedad | Por defecto | Qué decide |
|---|---|---|
| `maximumPoolSize` | 10 | Cuántas conexiones como máximo |
| `minimumIdle` | Igual al máximo | Cuántas se mantienen abiertas sin uso. Dejarlo igual al máximo da un pool de tamaño fijo, que es lo recomendado |
| `connectionTimeout` | 30 s | Cuánto espera quien pide una conexión antes de recibir un error |
| `maxLifetime` | 30 min | Edad máxima de una conexión. Al cumplirla se retira, cuando no esté en uso |
| `keepaliveTime` | 2 min | Cada cuánto se comprueba que una conexión ociosa sigue viva |
| `idleTimeout` | 10 min | Cuánto puede estar ociosa una conexión antes de retirarse. Solo aplica si `minimumIdle` es menor que el máximo |
| `validationTimeout` | 5 s | Cuánto se espera al comprobar que una conexión sirve |
| `leakDetectionThreshold` | Desactivado | Si una conexión lleva prestada más de este tiempo, se registra un aviso con la traza de quien la tomó |
| `poolName` | Generado | El nombre que aparece en registros y métricas |

En Spring Boot van bajo `spring.datasource.hikari`, con guiones: `maximum-pool-size`, `connection-timeout`.

### Cuántas conexiones

La intuición dice "más conexiones, más rendimiento". La realidad es que la base solo puede ejecutar de verdad tantas consultas a la vez como procesadores tiene. A partir de ahí, más conexiones significan más cambios de contexto, más memoria y más contención por los mismos bloqueos: el rendimiento total **baja**.

La documentación de HikariCP propone como punto de partida, pensando en el servidor de la base:

```
conexiones = (núcleos × 2) + discos efectivos
```

Para un servidor de cuatro núcleos, unas nueve o diez. No por instancia de tu aplicación: **en total**.

| Con un pool pequeño | Con un pool enorme |
|---|---|
| Las peticiones que sobran esperan en una fila ordenada, en tu aplicación | Todas entran a la base a la vez y compiten |
| La base trabaja a su ritmo óptimo | La base se satura y todo se vuelve lento |
| Un pico se nota como espera | Un pico tumba la base, y con ella a todos los servicios que la usan |

Es un punto de partida, no una ley: se confirma midiendo (sesión 05).

### La cuenta que hay que hacer siempre

```
instancias × maximumPoolSize  ≤  conexiones que la base permite − reserva
```

La reserva es para administración, migraciones y otros servicios. Y cuenta las instancias **durante un despliegue**, cuando conviven las viejas y las nuevas, y el máximo al que puede llegar el autoescalado.

### Tiempos

| Propiedad | Criterio |
|---|---|
| `connectionTimeout` | Corto: unos pocos segundos. Si no hay conexión en ese tiempo, es mejor fallar rápido que tener al usuario esperando treinta segundos y a los hilos acumulándose |
| `maxLifetime` | Varios minutos **menos** que cualquier límite que impongan la base, un proxy o un cortafuegos sobre la duración de una conexión. Si ellos la cortan primero, tu aplicación descubre la conexión rota al usarla |
| `keepaliveTime` | Menor que `maxLifetime` y menor que el tiempo de inactividad tras el cual la red corta conexiones ociosas |
| `leakDetectionThreshold` | Algo mayor que tu transacción más larga legítima. Demasiado bajo produce avisos falsos |

## Ejemplo

### Antes

<!-- file: ejemplo/antes/application.yaml -->
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/tienda
    username: tienda
    password: Tienda2026!
    hikari:
      maximum-pool-size: 200
      minimum-idle: 1
      connection-timeout: 120000
      max-lifetime: 0
```

Seis instancias del servicio, y una base con `max_connections=300`.

### Análisis

| Problema | Consecuencia |
|---|---|
| Pool de 200 "para que no falte" | La base tiene ocho núcleos. Con 200 consultas simultáneas se arrastra |
| 6 instancias × 200 = 1200 posibles, contra un límite de 300 | En un pico, las conexiones nuevas se rechazan con un error de la base, no del pool. Y durante un despliegue son doce instancias |
| `minimum-idle: 1` | Tras un rato tranquilo queda una conexión; el pico siguiente paga la apertura de todas las demás, justo cuando menos conviene |
| Espera de dos minutos | Ante un problema, cada petición retiene un hilo dos minutos. El servicio entero se queda sin hilos y parece caído |
| `max-lifetime: 0` (sin límite) | Las conexiones viven para siempre. Cuando la base o un proxy las corta, la aplicación se entera al usarlas |
| Contraseña en el archivo | Está en el repositorio |

### Después

<!-- file: ejemplo/despues/application.yaml -->
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/tienda
    username: tienda
    password: ${DB_PASSWORD}             # llega del entorno, no del repositorio
    hikari:
      pool-name: pedidos
      maximum-pool-size: 8               # 6 instancias x 8 = 48; hasta 96 durante un despliegue; el límite es 300
      # minimum-idle no se declara: queda igual al máximo y el pool es de tamaño fijo
      connection-timeout: 3000           # falla rápido
      max-lifetime: 1500000              # 25 min: el proxy corta a los 30
      keepalive-time: 120000             # 2 min
      leak-detection-threshold: 20000    # ninguna operación legítima retiene una conexión 20 s
```

- El tamaño sale de una cuenta, y la cuenta está escrita al lado.
- Tamaño fijo: no hay aperturas en mitad de un pico.
- Se falla en tres segundos, no en ciento veinte.
- Las conexiones se renuevan antes de que nadie las corte.
- El pool tiene nombre: se distingue en los registros y en las métricas.

Los dos archivos se cargaron tal cual en una aplicación Spring Boot 4.1.1 con Actuator, HikariCP 7.1.0 y PostgreSQL 17, y los valores que informa el pool coinciden con los del archivo. Spring Boot 4.1.1 trae por sí solo HikariCP 7.0.2: para usar la 7.1 hay que fijar su versión en el `pom.xml`.

## Errores comunes

- Subir el tamaño del pool como primera reacción a un `Connection is not available`. Casi siempre el problema es quién retiene las conexiones, no cuántas hay.
- Calcular por instancia y olvidar multiplicar.
- Olvidar el despliegue gradual y el autoescalado en la cuenta.
- `connectionTimeout` de minutos.
- `maxLifetime` mayor que el límite de la base o del proxy.
- `minimumIdle` bajo "para ahorrar".
- Copiar una configuración de un blog sin hacer la cuenta propia.
- Dejar la detección de fugas desactivada en todos los ambientes.

## Taller

**Objetivo:** dimensionar un pool con una cuenta y demostrar, midiendo, que más grande no es más rápido.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../hexagonal/), el dato que alimenta la cuenta es cuánto retiene la conexión cada **caso de uso** dentro de su unidad de trabajo, no cuánto tarda cada consulta. Mídelo por caso de uso.

Con el [entorno](../entorno/) (PostgreSQL limitado a 30 conexiones) y una aplicación Spring Boot mínima con un extremo que ejecute una consulta de unos 20 ms (por ejemplo, con `pg_sleep(0.02)`).

Un aviso sobre `pg_sleep`, comprobado al preparar el curso (PostgreSQL 17, 12 núcleos visibles, 50 usuarios): solo **espera**, no gasta CPU de la base, y con ella el rendimiento sube casi lineal con el pool (de 2 a 28 conexiones pasó de unas 85 a unas 1150 peticiones/s). Sirve para los pasos 1, 2, 6, 7 y 8, pero no para el barrido del paso 3. Añade un segundo extremo que **trabaje**, por ejemplo con `select count(*) from generate_series(1, 300000)`, y haz con él los pasos 3 a 5: con la base limitada a un núcleo, el máximo aparece entre 2 y 5 conexiones y por encima solo crece la latencia.

1. Arranca con la configuración por defecto. Averigua, sin leer la documentación, qué valores está usando: actívalo en el registro o consúltalo por las métricas.
2. Lanza carga con 50 usuarios concurrentes durante un minuto. Anota peticiones por segundo y percentil 95.
3. Repite con `maximum-pool-size` en 2, 5, 10, 20 y 28. Haz la tabla y la gráfica. ¿Dónde está el máximo? ¿Qué pasa por encima?
4. Mira cuántos núcleos tiene tu contenedor de PostgreSQL y aplica la fórmula. ¿Coincide con lo que mediste?
5. Limita el contenedor de la base a un núcleo y repite los pasos 3 y 4.
6. Arranca tres instancias de la aplicación con un pool de 15. Lanza carga. Copia el error que aparece y di si viene de HikariCP o de PostgreSQL. Haz la cuenta y corrígelo.
7. Con `connection-timeout` en 30 s, detén la base durante la carga. ¿Cuánto tarda cada petición en fallar y cuántos hilos de la aplicación quedan ocupados? Repite con 2 s.
8. Configura en PostgreSQL que las conexiones ociosas se cierren al minuto (o reinicia la base a mano). Con `max-lifetime` en 30 minutos, ¿qué ve el usuario? Ajusta los tiempos para que no vea nada.
9. Pon `minimum-idle` en 1 y deja la aplicación cinco minutos sin tráfico. Lanza un pico. Compara el percentil 95 de los primeros segundos con el del pool de tamaño fijo.
10. Escribe la configuración final, con un comentario junto a cada valor que diga de qué cuenta o de qué medición salió.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La tabla del paso 3 muestra que el rendimiento no crece con el tamaño | Mediciones |
| Tres instancias conviven sin errores de conexión | Paso 6, con la cuenta escrita |
| Cada valor de la configuración final tiene su justificación | Paso 10 |

**Entrega:** rama `taller/hikari-02`, con el código, los resultados y `ANALISIS.md` en `taller/solucion/`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

El tamaño del pool es una decisión que se toma entre el equipo del servicio y quien administra la base, porque la base es compartida. Un servicio que sube su pool por su cuenta puede dejar sin conexiones a otros cinco.

## Preguntas senior

1. ¿Por qué un pool más pequeño puede dar más rendimiento?
2. ¿Cómo calculas el tamaño del pool de un servicio con varias instancias?
3. ¿Qué relación debe haber entre `maxLifetime` y los límites de la base o de la red?
4. ¿Por qué conviene un `connectionTimeout` corto?
5. ¿Tamaño fijo o variable? Argumenta.
