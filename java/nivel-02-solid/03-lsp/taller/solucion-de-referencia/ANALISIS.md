# Taller LSP (solución de referencia)

Carpetas y ejecución:

- `a-clasico/`: `java a-clasico/Main.java`. Termina con código 1 si la demostración no se cumple.
- `a-alternativa/`: `java a-alternativa/Main.java`.
- `b-documentos/`: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (5 pruebas, 0 fallos). Los archivos de `b-documentos/no-compila/` deben fallar al compilar: `javac -d out Documento*.java no-compila/EditarComoDocumento.java` (y así con cada uno).
- `doble-de-prueba/`: prototipo del punto 4 de la Parte B, se compila contra el doble real de `hikaricp/06`: `javac -d out doble-de-prueba/Main.java ../../../../../hikaricp/06-el-pool-en-un-servicio-hexagonal/ejemplo/src/ConexionesDePrueba.java && java -cp out Main`.

## Parte A — El clásico

`Cuadrado extends Rectangulo` sobrescribe `setAncho` y `setAlto` para mantener los lados iguales. `comprobarDuplicaArea` (el cliente) espera que `duplicarAncho` duplique el área. Salida real:

```
Rectángulo: área duplicada = true (12)
Cuadrado:   área duplicada = false (36)
```

El cuadrado de lado 3 pasa a 6 x 6 = 36, cuatro veces el área. `Cuadrado` refuerza la postcondición de `setAncho` ("solo cambia el ancho") y rompe al cliente.

Dos diseños alternativos:

1. **Figuras inmutables** (`a-alternativa/`, el elegido): `Rectangulo` y `Cuadrado` son `record` independientes que implementan `Figura { int area(); }`. "Cambiar el ancho" devuelve una figura nueva (`conAncho`); un cuadrado que se ensancha se convierte explícitamente en rectángulo (`comoRectangulo().conAncho(6)`), y el tipo resultante lo dice. Sin mutación, la geometría ("un cuadrado es un rectángulo") deja de chocar con el comportamiento.
2. **Sin relación de herencia y mutables**: `Cuadrado` con un único `setLado` y `Rectangulo` con `setAncho`/`setAlto`, ambos implementando `Figura`. Funciona, pero un cliente que necesite "cambiar dimensiones" debe distinguir los tipos.

Elegiría el 1: elimina la causa (mutación de un estado que una subclase restringe), permite tratar todas las figuras por igual cuando solo se lee el área, y hace imposible un "cuadrado" de lados distintos.

## Parte B — El caso de negocio

### 1. Qué regla viola `DocumentoFirmado`

Debilita la postcondición de `editar`: el contrato de `Documento` es "después de `editar(x)`, `contenido()` devuelve `x`", y la subclase no la cumple. Salida real del inicio: `acta.pdf: Texto del acta`, sin "(revisado)" y sin ningún aviso. Es **más grave** que lanzar excepción: la excepción también viola LSP (refuerza la precondición: "solo se puede editar si no está firmado"), pero al menos falla de forma visible donde ocurre. El fallo silencioso hace que el llamador crea que editó, guarde, notifique "documento revisado" y el error aparezca mucho después, lejos de la causa.

### 2. Editar un firmado como error de compilación

`Documento` (interfaz) solo promete leer: `nombre()` y `contenido()`. `DocumentoEditable` agrega `editar` y `firmar`. `DocumentoFirmado` (record inmutable) implementa `Documento` y no tiene `editar`. Comprobado con `javac`:

- `no-compila/EditarFirmado.java` → `error: cannot find symbol`.
- `no-compila/EditarComoDocumento.java` → `error: cannot find symbol` (el contrato común no promete edición).
- `no-compila/FirmadoComoEditable.java` → `error: incompatible types: DocumentoFirmado cannot be converted to DocumentoEditable`.

El bucle del `Main` original se parte en dos: se edita una `List<DocumentoEditable>` y se lee una `List<Documento>`.

### 3. La transición sin herencia

`DocumentoEditable.firmar(firmante)` devuelve un `DocumentoFirmado` nuevo con el contenido de ese momento. La firma es una instantánea: editar después el borrador no altera el firmado (salida real: `Firmado: Texto del contrato (revisado) | borrador: Borrador v2`; la prueba "la firma congela el contenido" lo verifica). Si el negocio exige que el borrador deje de existir al firmarse, el repositorio reemplaza el editable por el firmado al guardar; el tipo de la referencia que tiene cada cliente dice qué puede hacer con ella.

### 4. Un método sobrescrito vacío o que lanza `UnsupportedOperationException`

En este mismo repo: `hikaricp/06-el-pool-en-un-servicio-hexagonal/ejemplo/src/ConexionesDePrueba.java`. Es un doble de prueba hecho con `Proxy`: el `DataSource` lanza `UnsupportedOperationException` para todo lo que no sea `getConnection`, y la `Connection` devuelve `null` para todos los métodos (solo anota `commit`, `rollback` y `close`). Hoy funciona porque el código probado solo llama a esos métodos y a `setAutoCommit`. Pero el doble no cumple el contrato de `Connection`: si el adaptador empezara a llamar `getAutoCommit()` o `isClosed()`, la prueba explotaría con `NullPointerException` (el proxy devuelve `null` para un `boolean`) y no por un error del adaptador. `doble-de-prueba/Main.java` ejercita ese mismo archivo (se compila junto con `ConexionesDePrueba.java` del repo, sin copiarlo): pide una conexión al `DataSource` del doble, llama `setAutoCommit`, `commit` y `close` (salida real `anotado por el doble: [tomar, confirmar, devolver]`) y después `getAutoCommit()`, con salida real `getAutoCommit con el doble: NullPointerException (el real devuelve un boolean)`. Corrección: que el doble devuelva valores coherentes con el contrato para los métodos de consulta (`getAutoCommit` el último valor fijado, `isClosed` según se haya llamado `close`) y lance `UnsupportedOperationException` con un mensaje claro para el resto, de modo que la falla diga "el doble no soporta X" en lugar de un `NullPointerException`. Es un doble de prueba que viola LSP, el caso que la sección 6 de la lección describe.
