# Taller 05-04 — Inversión de dependencias aplicada a arquitectura (solución)

`mvn test` ejecuta 5 pruebas. El "SDK" de Stripe se simula en `tienda.proveedor.stripe` con su propio vocabulario (`createPaymentIntent`, centavos, `succeeded`).

## 1. Imports de la clase de servicio original (`tienda.antes.service.PedidoService`)

| Import | Clase |
|---|---|
| `org.springframework.stereotype.Service` | Framework |
| `tienda.proveedor.stripe.StripeClient` | Infraestructura (SDK de un proveedor) |
| `java.util.HashMap`, `java.util.Map` | Lenguaje |
| (ninguno) | Negocio: el servicio no tiene tipos propios del negocio; trabaja con `long` y `String` |

## 2. Inversión aplicada a los pagos

- Interfaz en el paquete del negocio, con su vocabulario: `tienda.pedidos.dominio.PasarelaPagos.cobrar(Dinero, MedioDePago) : ResultadoPago`.
- Implementación movida a `tienda.pedidos.infraestructura.PasarelaStripe`, que traduce pesos a centavos y `status` a `Aprobado`/`Rechazado`.
- `PedidosConfig` ensambla: `ConfirmarPedido` es una clase sin anotaciones creada con `@Bean`.

## 3. Antes y después (flechas = `import`, no llamadas)

```
Antes:   antes.service.PedidoService ──► proveedor.stripe.StripeClient
                                     ──► org.springframework.stereotype

Después: pedidos.infraestructura.PasarelaStripe ──► pedidos.dominio.PasarelaPagos
                                                ──► proveedor.stripe.StripeClient
         pedidos.dominio.ConfirmarPedido        ──► pedidos.dominio.*  (nada más)
```

En ejecución, `ConfirmarPedido` sigue llamando a Stripe; en el código, la flecha entre negocio y proveedor cambió de sentido.

## 4. Prueba con implementación en memoria

`ConfirmarPedidoTest` usa `PasarelaFalsa` (aprueba o rechaza a voluntad y registra los cobros) y `RepositorioPedidosEnMemoria`. Una tercera prueba arranca el contexto de Spring para comprobar que el adaptador real queda conectado.

## 5. Regla de ArchUnit (ejecutada)

```java
noClasses().that().resideInAPackage("..dominio..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infraestructura..", "org.springframework..", "..proveedor..");
```

`ReglaDeDependenciasTest` la comprueba sobre el dominio (pasa) y aplica la misma regla al servicio original para mostrar que falla nombrando `StripeClient` y `@Service`.
