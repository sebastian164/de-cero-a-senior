# Composite — análisis

1. **Costo y personas**: `Unidad` es el componente; `Empleado` la hoja y `Area` el contenedor. `costoMensual()` y `personas()` funcionan igual en cualquier nodo (empresa: $39.500.000 y 4 personas; desarrollo: $12.500.000).

2. **Ciclos**: `Area.agregar` rechaza agregarse a sí misma y cualquier área que ya la contenga en algún nivel (`contiene` recorre el subárbol). Probado con ciclo directo (`empresa.agregar(empresa)`) e indirectos (`desarrollo.agregar(empresa)`).

3. **"Los que ganan más de X"**: hubo que tocar **las tres** piezas: la interfaz `Unidad` y las dos clases. Es la debilidad de poner las operaciones dentro de los nodos: cada operación nueva toca todos los tipos. Visitor (o `sealed` + `switch`) invierte el costo: operaciones nuevas baratas, tipos de nodo nuevos caros.

4. **`sealed interface` + `switch`** (`Organigrama`): los nodos son `record` sin comportamiento y cada operación es una función con `switch` exhaustivo. Agregar `queGananMasDe` fue escribir una función, sin tocar los nodos. La preferiría cuando los tipos de nodo son pocos y estables (empleado/área lo son) y las operaciones crecen (reportes de nómina, exportaciones, auditorías). Prefiero la clásica cuando aparecen tipos de nodo nuevos con frecuencia o cuando la operación necesita estado encapsulado del nodo. La prueba `laVersionSealedDaLoMismo` comprueba que ambas dan los mismos resultados.
