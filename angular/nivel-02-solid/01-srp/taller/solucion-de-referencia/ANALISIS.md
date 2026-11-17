# Taller n2-01 — SRP

Componente de partida: `antes/pantalla-factura.component.ts` (210 líneas, compilado y probado como línea base en `pantalla.spec.ts`).

## 1. Actores: quién pediría cada cambio

| Actor | Qué pide cambiar | Dónde estaba antes |
|---|---|---|
| Contabilidad | Tarifa del IVA, base del IVA, formato del CSV | `calcularIva`, `exportarCsv` |
| Ventas | Descuento por volumen y por tipo de cliente | `calcularDescuento` |
| Mercadeo | Cupones y sus condiciones | `aplicarCupon` (con `new Date()` adentro) |
| Equipo del backend | Nombres de campos (`fecha_emision`, `valor_unitario`, `cliente.nombre_completo`), códigos de error | `ngOnInit`, toda la plantilla |
| Diseño / UX | Cómo se ve la factura, formatos de fecha y dinero | plantilla, `formatearFecha`, `formatearDinero` |
| Soporte / operación | Caché local, mensajes de error | `localStorage` en `ngOnInit` |
| Equipo de notificaciones | Cómo se envía un correo | `enviarPorCorreo` |

Siete actores para un solo archivo.

## 2. Separación

```
despues/
├── dominio/factura.ts       reglas: subtotal, descuento, IVA, total, sePuedeEnviar   (Contabilidad, Ventas)
├── dominio/cupones.ts       cupones, con la fecha como parámetro                     (Mercadeo)
├── datos/facturas.api.ts    HTTP, DTO y adaptador aFactura                            (backend)
├── exportacion/csv.ts       formato CSV (puro) y Descargador (navegador)             (Contabilidad)
├── ui/resumen-factura.ts    presentación con las tuberías date y currency             (Diseño)
└── pantalla-factura.ts      contenedor: estado de carga, correo, cupón                (coordina)
```

## 3. Prueba de una regla sin Angular

`dominio.spec.ts` no importa nada de `@angular/*`: descuento por tramos, IVA sobre la base descontada, cupones con fecha fija y CSV.

## 4. Qué se dejó junto y por qué

- **El estado de la pantalla** (correo escrito, cupón, mensaje) sigue en el contenedor: solo esa pantalla lo usa. Un almacén sería burocracia.
- **Enviar por correo** quedó como método de `FacturasApi` y no como un servicio aparte: hoy es un `POST` de una línea al mismo backend. Si aparece un segundo consumidor o otra vía (SMS), se separa.
- **La caché en `localStorage` se eliminó**, no se movió: mostraba datos viejos sin avisar. Si hiciera falta, iría en `datos/`.
- **`descuentoComercial` e `iva` en el mismo archivo:** cambian juntos (el IVA se calcula sobre la base ya descontada).

## 5. Tres cambios: archivos tocados antes y después

| Cambio | Antes | Después |
|---|---|---|
| El IVA pasa al 16 % | `pantalla-factura.component.ts` (la clase y, por arrastre, sus pruebas de pantalla) | `dominio/factura.ts` (una constante) y `dominio.spec.ts` |
| El backend renombra `valor_unitario` a `precio` | El componente: plantilla, `calcularSubtotal`, `exportarCsv` (3 lugares) | `datos/facturas.api.ts` (el DTO y `aFactura`) |
| Diseño quiere la fecha como "14 de marzo de 2026" | El componente (`formatearFecha`) | `ui/resumen-factura.component.ts` (el formato de la tubería `date`) |

En los tres casos, antes se tocaba el mismo archivo que tiene todo lo demás; después se toca un archivo cuyo único motivo de cambio es ese.

## Lo que encontró la prueba E2E

La primera versión del contenedor declaraba `readonly id = input('F-2')`. En la aplicación, con `provideRouter(rutas, withComponentInputBinding())`, la página pidió `/api/facturas/undefined`: el enrutador asigna `undefined` a toda entrada cuyo nombre no viene en la ruta (parámetros, consulta o `data`), y el valor por defecto se pierde. La corrección fue declarar `id` como `input.required` y pasarlo por `data: { id: 'F-2' }` en la ruta.
