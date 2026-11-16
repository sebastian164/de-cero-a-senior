# Arquitectura hexagonal con Java

Módulo práctico para aprender arquitectura hexagonal construyendo. Va después del [Nivel 5 de Java](../java/nivel-05-arquitectura/), cuya [sesión 06](../java/nivel-05-arquitectura/06-arquitectura-hexagonal/) da el panorama en una hora; aquí se hace entero.

Tiene dos hilos que avanzan a la vez:

- **El ejemplo es un banco.** Un servicio de cuentas y transferencias, con código real que se compila y se ejecuta en cada sesión. Está completo en [`proyecto/`](proyecto/). Con él se explican también las **transacciones**: atomicidad, idempotencia, aislamiento y concurrencia, con pruebas que provocan cada fallo a propósito.
- **Tu taller es una tienda.** Partes de un [servicio de pedidos acoplado](01-el-problema/) y lo reconstruyes sesión a sesión. No hay solución publicada: el banco te enseña la forma, pero el dominio es otro y las decisiones son tuyas. Al terminar, ese servicio es la base de los talleres de [Kafka y sagas](../kafka-y-sagas/) y de [HikariCP](../hikaricp/).

## Orden

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [El problema](01-el-problema/) | Reconocer el acoplamiento y nombrar las piezas: dominio, aplicación, puertos y adaptadores |
| 02 | [El dominio](02-el-dominio/) | Poner las reglas en objetos que no dependen de nada y probarlas en milisegundos |
| 03 | [Puertos y casos de uso](03-puertos-y-casos-de-uso/) | Diseñar puertos de entrada y de salida, y coordinar el dominio desde un caso de uso |
| 04 | [Transacciones](04-transacciones/) | ACID, la frontera de la transacción, idempotencia y bandeja de salida |
| 05 | [Concurrencia y aislamiento](05-concurrencia-y-aislamiento/) | Actualización perdida, niveles de aislamiento, bloqueo optimista y pesimista, reintentos |
| 06 | [Adaptadores y composición](06-adaptadores-y-composicion/) | Exponer los mismos casos de uso por HTTP y por consola, y conectarlo todo en un solo sitio |
| 07 | [Persistencia con ORM](07-persistencia-con-orm/) | Un adaptador con JPA, Hibernate y PostgreSQL sin tocar el núcleo |
| 08 | [Pruebas y límites](08-pruebas-y-limites/) | Probar cada capa, automatizar la regla de dependencias y saber cuándo no usar esto |

## El proyecto del banco

```
proyecto/
  src/banco/
    dominio/                 Cuenta, Dinero, Transferencia, Movimiento
    aplicacion/              ServicioDeCuentas y sus puertos de entrada y de salida
    adaptadores/entrada/     consola y HTTP
    adaptadores/salida/      memoria (con transacciones y versión) y reloj
    Main.java                la raíz de composición
  pruebas/banco/             79 comprobaciones, sin dependencias
proyecto-jpa/                el mismo núcleo, con Spring Boot, JPA y PostgreSQL
```

```bash
cd proyecto
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida banco.TodasLasPruebas
```

## Qué código se ejecutó

| Parte | Estado |
|---|---|
| [`proyecto/`](proyecto/): dominio, aplicación, adaptadores de memoria, consola y HTTP, y todas las pruebas | Compilado con `-Xlint:all -Werror` y ejecutado con JDK 21. La prueba con hilos se repitió varias veces |
| [`proyecto-jpa/`](proyecto-jpa/): Spring Boot, JPA, Hibernate y PostgreSQL | Compilado y ejecutado con Spring Boot 4.1.1 (Hibernate 7.4.5), JDK 21 y PostgreSQL 17: las órdenes `curl` de la sesión 07, el contrato del repositorio de la sesión 08, la prueba de reintento y una de 8 hilos con transferencias cruzadas. Esa prueba encontró interbloqueos; se corrigieron con `order_updates` en `application.yaml` |
| SQL de las sesiones 04 y 05 | Ejecutado en PostgreSQL 17 con dos conexiones a la vez: la transferencia con `COMMIT` y `ROLLBACK`, la actualización perdida en `READ COMMITTED`, la actualización atómica, el bloqueo optimista, el sesgo de escritura en `REPEATABLE READ` y su cancelación en `SERIALIZABLE`, y el interbloqueo con `FOR UPDATE` sin orden y sin él con `ORDER BY id` |

## Qué necesitas

- JDK 21. Las sesiones 01 a 06 y la 08 no usan nada más: ni Maven, ni Spring, ni base de datos.
- Para la sesión 07: Maven y Docker.
- Haber hecho, o tener claros, los niveles 2 a 5 de [Java](../java/): objetos, SOLID y pruebas.

Los archivos de código están también dentro de cada lección, precedidos de su ruta, para leerlos junto a la explicación.
