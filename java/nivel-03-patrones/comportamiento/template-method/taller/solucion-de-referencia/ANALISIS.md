# Template Method — análisis

1. **Template Method** (`GeneradorDocumento.generar`, `final`):
   | Paso | Tipo |
   |---|---|
   | Validar datos comunes (cliente, líneas) | fijo (`private`) |
   | `validarAdicional` | gancho, vacío por defecto |
   | `calcularTotales` | abstracto |
   | Numerar (consecutivo por prefijo) | fijo, con `prefijo()` abstracto |
   | `renderizar` | abstracto |
   | Auditar | fijo |
   Si falla la validación, no se consume consecutivo ni se audita (`validacionComunYNadaSeNumeraSiFalla`).

2. **Composición** (`TipoDocumento` + `GeneradorCompuesto`): los pasos variables son datos (prefijo, `Function` de totales, `Render`, lista de validaciones extra) y los tipos son constantes. Ambas versiones producen exactamente el mismo texto y auditoría (pruebas `herencia_...` y `composicion_...`).
   | | Herencia | Composición |
   |---|---|---|
   | Líneas de código (tipos + generador, sin comentarios ni vacías) | 55 | 34 |
   | Claridad | el esqueleto se lee en un método; cada tipo es una clase con nombre | el esqueleto también en un método; los tipos son tres constantes compactas |
   | Prueba | hay que instanciar una subclase | cada función se prueba sola |
   | Combinar | una subclase por combinación | se pueden mezclar funciones (totales de una, render de otra) |

3. **Paso extra de la nota crédito** (referenciar la factura original): en herencia, se sobrescribe el gancho `validarAdicional` (hubo que agregar el gancho a la clase base: el autor de la base debe prever los puntos de extensión). En composición, se agrega una función a `validacionesExtra` de ese tipo, sin tocar el generador; si el paso extra hubiera de ir en otro punto del flujo, en ambas versiones habría que tocar el esqueleto.

4. **Probar los totales aislados**: más fácil en **composición**: `TiposDocumento.FACTURA.totales().apply(lineas)` es una función pura. En herencia, `calcularTotales` es `protected` y vive en una subclase cuyo constructor exige numerador y auditoría (la prueba pasa `null`, una señal de que la prueba está forzando el diseño). En este taller se mitigó extrayendo `Calculos`, que es justamente moverse hacia composición.

5. **`JdbcTemplate` y callbacks**: el esqueleto (obtener conexión, preparar la sentencia, ejecutar, recorrer el `ResultSet`, traducir `SQLException`, cerrar recursos) es fijo, y lo único variable es "cómo mapear una fila" o "cómo fijar los parámetros". Si obligara a heredar, cada consulta sería una subclase, no se podría combinar con otra clase base (Java tiene herencia simple), el estado del template se mezclaría con el del usuario y sería difícil de probar. Con callbacks (`RowMapper`, `PreparedStatementSetter`, `ResultSetExtractor`), una sola instancia sin estado sirve para todo y lo variable es una lambda. Es Template Method por composición.
