# Taller 01 — Cómo separaría `Pedidos.java`

```
                 EXTERIOR (adaptadores)                                    EXTERIOR (adaptadores)
  ┌───────────────────────────────┐                             ┌───────────────────────────────────┐
  │ HTTP /pedidos (formulario)    │                             │ Archivo pedidos.txt / base de datos│
  │ Mensaje "crear pedido"        │                             │ Catálogo de precios (Map)          │
  │ Consola                       │                             │ Existencias (HashMap)              │
  └──────────────┬────────────────┘                             │ Reloj (LocalDateTime.now)          │
                 │ traduce a datos simples                      │ Correo (println)                   │
                 ▼                                              └───────────────▲───────────────────┘
        puertos de entrada                                                      │ implementan
  CrearPedido · ConfirmarPedido · CancelarPedido · ConsultarPedido      puertos de salida
                 │                                              RepositorioDePedidos · Catalogo ·
                 ▼                                              Inventario · Reloj · Notificador
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ APLICACIÓN: ServicioDePedidos                                            │
  │   1. pide precios y existencias  2. el dominio decide  3. guarda y avisa │
  │   (todo dentro de una unidad de trabajo: o se descuenta y se guarda, o   │
  │    no pasa nada)                                                         │
  └───────────────────────────────┬──────────────────────────────────────────┘
                                  ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ DOMINIO (solo java.*)                                                    │
  │   Dinero · LineaDePedido (cantidad ≥ 1) · Pedido (cliente, ≥ 1 línea,    │
  │   tope 5.000.000, estados PENDIENTE→CONFIRMADO/CANCELADO) ·              │
  │   Existencias (no se vende más de lo que hay)                            │
  └──────────────────────────────────────────────────────────────────────────┘
```

Las flechas de dependencia apuntan hacia dentro: HTTP conoce `CrearPedido`; el servicio conoce `Inventario`, no el `HashMap`;
el dominio no conoce a nadie. El defecto del paso 5 se resuelve en dos sitios: el dominio valida **todo** el pedido antes de
descontar, y la unidad de trabajo garantiza que descuento y guardado van juntos.
