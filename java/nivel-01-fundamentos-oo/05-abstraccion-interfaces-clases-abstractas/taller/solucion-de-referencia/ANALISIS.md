# Taller — Abstracción: medios de pago (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (12 pruebas, 0 fallos).

## 1. Diseño antes de programar

```
             «interface» MedioDePago
                 pagar(monto): Comprobante
        ┌────────────┬──────────────┬────────────────┬──────────────────┐
  «abstract»        Efectivo     Transferencia   BilleteraDigital
  PagoConTarjeta    (+calcularCambio)
  (16 dígitos, últimos 4,
   pagar = monto + comision)
     ┌──────┴──────┐
 TarjetaCredito  TarjetaDebito
   (2 %)            (0 %)
```

- **Interfaz `MedioDePago`**: es lo único que todos comparten (`pagar`). No hay estado común a los cuatro medios, así que no hay nada que poner en una clase base. Una interfaz permite que un medio futuro implemente el contrato sin heredar nada.
- **Clase abstracta `PagoConTarjeta`**: crédito y débito comparten estado (últimos cuatro dígitos) y la misma regla de validación, y el mismo algoritmo de pago que solo varía en la comisión. Eso es una plantilla: `pagar` es `final` y las subclases definen `comision` y `tipo`.
- **Clases concretas**: `TarjetaCredito`, `TarjetaDebito`, `Efectivo`, `Transferencia`, `BilleteraDigital`; todas `final`.
- `Comprobante` es un `record`: un dato inmutable sin comportamiento.

## 2. Implementación

Ver los archivos. Salida real de `Main` (50.000 con cada medio): crédito cobra 51000, el resto 50000; el efectivo informa `cambio 50000` al recibir 100.000.

## 3. `cobrar(MedioDePago medio, long monto)`

`Caja.cobrar` llama a `medio.pagar(monto)` e imprime el comprobante; no tiene ningún `instanceof` ni conoce las clases concretas.

## 4. ¿`calcularCambio` en el contrato común?

No. Solo el efectivo tiene cambio. Si estuviera en `MedioDePago`, las tarjetas, la transferencia y la billetera tendrían que implementarlo devolviendo 0 o lanzando `UnsupportedOperationException`: un método que miente o explota, que obliga a quien llama a saber qué medio tiene en la mano (justo lo que el contrato debía evitar) y que es la puerta a violar LSP e ISP. Quien necesita el cambio sabe que está cobrando en efectivo y usa `Efectivo` directamente; además el cambio aparece en la descripción del comprobante.

## 5. Billetera digital

Se agregó `BilleteraDigital.java`. Clases existentes modificadas: **0**. `Caja`, `Main` (salvo la línea que la agrega a la lista de demostración) y los demás medios quedaron igual. El valor cobrado y la validación del celular (10 dígitos que empiezan por 3) son supuestos del ejercicio.
