# Taller 05-06 — Hexagonal: transferencias entre cuentas (solución)

`mvn test` ejecuta 9 pruebas: 6 del caso de uso completo solo con adaptadores en memoria (incluida la CLI), 1 de REST + CLI contra el adaptador JPA (H2) y 2 reglas de ArchUnit.

## 1. Puertos

| Puerto | Tipo | Por qué esa granularidad |
|---|---|---|
| `TransferirDinero` | Entrada | Lo usan dos canales (REST y CLI) y las pruebas; además permite envolverlo con la transacción en la configuración sin que el servicio dependa de Spring |
| `CargarCuenta` | Salida | Lectura de la cuenta; un adaptador de solo lectura (réplica, caché) podría implementarlo solo |
| `GuardarCuenta` | Salida | Escritura separada de la lectura (ISP): quien solo consulta no ve `guardar` |
| `RegistroDeTransferencias` | Salida | Registro + suma del día. Van juntos porque el tope diario se calcula **sobre el registro**: separarlos permitiría que el cálculo y el registro usen almacenes distintos y se desincronicen |

No se hizo un puerto por tabla: los puertos hablan de necesidades del caso de uso.

## 2. Dominio y aplicación sin dependencias externas

- `dominio`: `Cuenta` (saldo suficiente y tope diario), `ServicioDeTransferencias` (misma cuenta, monto positivo), `Dinero`, `NumeroCuenta`, `Transferencia`.
- `aplicacion.servicio.TransferirDineroService`: carga, aplica reglas, guarda y **registra siempre**, aceptada o rechazada (con el motivo).
- ArchUnit verifica que `dominio` y `aplicacion` no importan `adaptador`, `configuracion`, Spring ni Jakarta.

## 3. Dos adaptadores primarios

- `adaptador.entrada.web.TransferenciaController`: JSON → comando; `201` si se acepta, `422` con el motivo si se rechaza, `400` si el formato es inválido.
- `adaptador.entrada.cli.TransferenciaCli`: argumentos → comando; código de salida 0/1/2.

Comparten el puerto, el servicio, el dominio y los adaptadores secundarios. Solo difiere la traducción de entrada y salida.

## 4. Dos adaptadores secundarios

- `adaptador.salida.memoria`: `CuentasEnMemoria` (implementa `CargarCuenta` y `GuardarCuenta`) y `RegistroEnMemoria`.
- `adaptador.salida.jpa.PersistenciaJpaAdapter`: implementa los tres puertos sobre dos entidades JPA. La suma del día es una consulta JPQL con intervalo `[00:00, 00:00 del día siguiente)`.

## 5. Caso de uso completo en memoria

`CasoDeUsoEnMemoriaTest`: transferencia aceptada, saldo insuficiente, tope diario (500 exacto se permite, 1 más no, al día siguiente se reinicia con un reloj controlable), misma cuenta, los rechazos quedan registrados y la CLI usa el mismo puerto.

## 6. ArchUnit

- `elHexagonoNoConoceAdaptadoresNiFrameworks`: dirección de las dependencias.
- `losAdaptadoresNoSeConocenEntreSi`: `onionArchitecture()` de ArchUnit con cuatro adaptadores; `web` no puede importar `jpa`, etc. La configuración se excluye por ser el ensamblador.

Decisión: la transacción se aplica en `TransferenciasConfig` con `TransactionTemplate` alrededor del puerto de entrada, así el servicio sigue sin Spring.

## Límites conocidos

- Un número de cuenta con formato inválido (`NumeroCuenta`) se traduce en cada adaptador: 400 por REST y código de salida 2 por CLI. No se registra como transferencia rechazada, porque nunca llegó a ser una transferencia.
- No hay control de concurrencia sobre el saldo: dos transferencias simultáneas desde la misma cuenta pueden leer el mismo saldo y sobregirarla. En producción se resuelve en el adaptador JPA con `@Version` (bloqueo optimista, con reintento) o `SELECT ... FOR UPDATE` (bloqueo pesimista); el dominio no cambia.

