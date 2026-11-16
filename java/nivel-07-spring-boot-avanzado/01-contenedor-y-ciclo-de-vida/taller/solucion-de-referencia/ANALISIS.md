# Taller 07-01 — Contenedor IoC y ciclo de vida (solución)

Spring Boot 4.1.1 (Spring Framework 7). `mvn test` ejecuta 5 pruebas, cada una arrancando contextos reales.

1. **Cuatro formas con tres implementaciones** (`notificadores`): `NotificadorCorreo` (`@Primary`), `NotificadorSms`, `NotificadorPush`. `Consumidores` recibe por constructor: el `@Primary` (correo), `@Qualifier("sms")`, `List<Notificador>` (las tres) y `Map<String, Notificador>` (claves `correo`, `sms`, `push`: los nombres de los beans).
2. **Fases del ciclo de vida** (`ciclo.BeanObservado` + `ObservadorDePostProcesamiento`). Orden real registrado por la prueba:
   ```
   1 constructor (con su dependencia)
   2 inyección por setter
   3 BeanNameAware: beanObservado
   4 BeanPostProcessor antes de inicializar (¿es proxy? false)
   5 @PostConstruct (¿es proxy? false)
   6 afterPropertiesSet
   7 BeanPostProcessor después de inicializar (¿es proxy? true)
   8 ApplicationReadyEvent
   9 @PreDestroy
   10 DisposableBean.destroy
   ```
   El bean tiene un método `@Async`, así que el contenedor lo envuelve en un proxy. **El proxy aparece en la fase 7, después de `@PostConstruct`**: los proxies de AOP se crean en `postProcessAfterInitialization`. Consecuencia práctica: dentro de `@PostConstruct` se está en el objeto real, y llamar desde ahí a un método `@Transactional` o `@Async` propio no pasa por el proxy. (Esto contradecía el orden de la lista del README, que se corrigió.)
3. **Dependencia circular**: `Pedidos(Facturacion)` y `Facturacion(Pedidos)` por constructor. Error real: *"Requested bean is currently in creation: Is there an unresolvable circular reference or an asynchronous initialization dependency?"* (`BeanCurrentlyInCreationException`). Rediseño: `Pedidos` publica `PedidoConfirmado` y `Facturacion` lo escucha con `@EventListener`; ninguno conoce al otro. Probado.
4. **`prototype` en singleton**: `TiendaConProblema` recibe un `Carrito` una sola vez; el "carrito nuevo" del segundo cliente ya tiene la leche del primero. `TiendaCorregida` usa `ObjectProvider<Carrito>.getObject()` y obtiene una instancia distinta cada vez.
5. **Bean condicional**: el fragmento del README (`@ConditionalOnProperty(name = "pagos.proveedor", havingValue = "stripe")`) junto con una pasarela simulada por defecto. El mismo código, sin recompilar, arranca con la simulada o con `--pagos.proveedor=stripe` (la prueba lo hace con argumentos de línea de comandos; también puedes repetirlo con `java -jar` sobre el jar empaquetado).
