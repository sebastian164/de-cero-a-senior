# El banco

El proyecto de ejemplo del módulo de [arquitectura hexagonal](../). Java 21 puro: sin Maven, sin Spring y sin base de datos.

Cada archivo se explica en la sesión donde aparece:

| Paquete | Qué hay | Sesión |
|---|---|---|
| `banco.dominio` | `Cuenta`, `Dinero`, `Transferencia`, `Movimiento` | [02](../02-el-dominio/) |
| `banco.aplicacion` | `ServicioDeCuentas`, puertos de entrada y de salida | [03](../03-puertos-y-casos-de-uso/) |
| `banco.adaptadores.salida.memoria` | `AlmacenEnMemoria`: transacciones, versión y bandeja de salida | [03](../03-puertos-y-casos-de-uso/), [04](../04-transacciones/), [05](../05-concurrencia-y-aislamiento/) |
| `banco.adaptadores.entrada` | `Consola` y `ServidorHttp` | [06](../06-adaptadores-y-composicion/) |
| `banco.Main` | La raíz de composición | [06](../06-adaptadores-y-composicion/) |
| `pruebas/` | Dominio, aplicación, transacciones, concurrencia, adaptadores, contrato y arquitectura | [02](../02-el-dominio/) a [08](../08-pruebas-y-limites/) |

## Compilar y probar

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida banco.TodasLasPruebas
```

En Windows, con PowerShell:

```powershell
javac -Xlint:all -Werror -d salida (Get-ChildItem -Recurse -Filter *.java src, pruebas | ForEach-Object FullName)
java -cp salida banco.TodasLasPruebas
```

## Usarlo

```bash
# Consola: una orden por línea. Los valores van en pesos.
java -cp salida banco.Main consola
#   abrir Ana 1000000
#   abrir Luis 200000
#   transferir orden-1 cuenta-1 cuenta-2 300000
#   extracto cuenta-1

# HTTP. Los valores van en centavos.
java -cp salida banco.Main http 8080
curl -s -X POST localhost:8080/cuentas -d 'titular=Ana&centavos=100000000'
curl -s -X POST localhost:8080/cuentas -d 'titular=Luis&centavos=20000000'
curl -s -X POST localhost:8080/transferencias -H 'Idempotency-Key: orden-1' \
  -d 'origen=cuenta-1&destino=cuenta-2&centavos=30000000'
curl -s localhost:8080/cuentas/cuenta-1
curl -s localhost:8080/cuentas/cuenta-1/movimientos
```

Los datos viven en memoria: al detener el proceso se pierden. La persistencia real está en [`../proyecto-jpa/`](../proyecto-jpa/).
