# Taller ISP — Servicio de usuarios (solución de referencia)

- Esta carpeta: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (8 pruebas, 0 fallos). `Main` imprime lo mismo que el inicio (`ana@correo.example`, `[ana]`) más el resumen del tablero.
- `comparacion/DobleAntes.java`: se compila con `../inicio/Main.java` (`javac -d out2 comparacion/DobleAntes.java ../inicio/Main.java && java -cp out2 PruebaAntes`) y muestra el doble que exigía la interfaz gorda.

## 1. Matriz cliente × método

| Método | Registro | Administración | Marketing | Métricas | Perfil |
|---|:-:|:-:|:-:|:-:|:-:|
| `registrar` | X | | | | |
| `buscar` | | | | | X |
| `listarTodos` | | X | | | |
| `cambiarContrasena` | | | | | X |
| `bloquear` | | X | | | |
| `eliminar` | | X | | | |
| `exportarCsv` | | | X | | |
| `enviarCorreoMasivo` | | | X | | |
| `contarActivosUltimoMes` | | | | X | |

Cada método tiene exactamente un cliente: las columnas no se solapan.

## 2. Interfaces segregadas

| Interfaz | Métodos | Justificación con la matriz |
|---|---|---|
| `RegistroDeUsuarios` | `registrar` | Columna Registro. Es la única que se expone a la app pública; separarla limita lo que un atacante podría alcanzar si esa capa se compromete. |
| `PerfilDeUsuario` | `buscar`, `cambiarContrasena` | Columna Perfil. |
| `AdministracionDeUsuarios` | `listarTodos`, `bloquear`, `eliminar` | Columna Administración. Operaciones destructivas que solo el panel debe ver. |
| `CampanasDeMarketing` | `exportarCsv`, `enviarCorreoMasivo` | Columna Marketing. |
| `MetricasDeUsuarios` | `contarActivosUltimoMes` | Columna Métricas. |

Como ninguna columna comparte métodos con otra, la matriz da directamente cinco interfaces; no hubo que decidir dónde poner un método compartido. `Main` (que no es uno de los cinco clientes) usa tres de ellas.

## 3. Una sola implementación, ¿y debería partirse?

`ServicioUsuariosEnMemoria` implementa las cinco. Además, `bloquear` y `cambiarContrasena`, que en el inicio eran métodos vacíos (una mentira del contrato), ahora hacen algo y tienen prueba.

¿Debería partirse? Por SRP, probablemente sí, a medida que crezca: los cinco grupos responden a actores distintos (producto, seguridad, operaciones, marketing, analítica) y comparten solo los datos de usuarios. El correo masivo, en un sistema real, usaría un proveedor de correo que nada tiene que ver con el registro; las métricas, una consulta analítica. Hoy, con un mapa en memoria y métodos de una línea, partirla repartiría el mismo mapa en cinco clases sin beneficio. La ventaja de haber segregado las interfaces primero es que la partición posterior no cambia a ningún cliente: cada uno depende de su interfaz, no de la clase.

## 4. Cliente y doble de prueba

`TableroMetricas` depende solo de `MetricasDeUsuarios`.

- **Antes** (`comparacion/DobleAntes.java`, contra la interfaz del inicio): el doble implementa 9 métodos, 8 de ellos lanzando `UnsupportedOperationException`; 13 líneas.
- **Después** (`solucion/Pruebas.java`): `new TableroMetricas(() -> 3)`. Una lambda, cero líneas de clase.

Además, el doble de antes se rompe cada vez que alguien agrega un método a `ServicioUsuarios`, aunque al tablero no le importe.

## 5. ¿Interfaces de un solo método?

Sí: `RegistroDeUsuarios` y `MetricasDeUsuarios`. No es un problema: responden a un cliente real con una necesidad real, se implementan con una lambda y se prueban trivialmente. Es lo que hace el propio framework (`Converter`, `ApplicationRunner`, `UserDetailsService`). Sería un problema si se partiera por método y no por cliente: muchas interfaces de un método que siempre se usan juntas obligan a inyectar varias dependencias para una sola necesidad.
