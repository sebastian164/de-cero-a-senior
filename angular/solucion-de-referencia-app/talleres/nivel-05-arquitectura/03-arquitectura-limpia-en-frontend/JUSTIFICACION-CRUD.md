# Por qué no se aplicaría esta estructura a una pantalla CRUD (punto 5)

Pantalla elegida: el mantenimiento de productos del taller n5-02 (lista, crear, editar nombre, borrar), (en esta aplicación existen su lista y su alta: `/api/n5-02/productos`).

Si se aplicaran las cuatro capas completas, saldrían: un modelo `Producto` (`dominio`), un puerto `RepositorioProductos`, cuatro casos de uso (`ListarProductos`, `CrearProducto`, `EditarProducto`, `BorrarProducto`) y un adaptador `ProductosHttp` con un mapeador. Cada caso de uso tendría una línea, `return this.repositorio.x(...)`: tres archivos que solo reenvían una llamada (el error común del README), más un mapeador entre dos tipos idénticos (otro error común).

Qué regla del cliente justificaría el dominio: ninguna. No hay cálculos que el usuario vea mientras interactúa, ni pasos condicionales, ni reglas de presentación con peso de negocio: lo único que hay son validaciones de longitud, que son del formulario, y toda decisión real (duplicados, permisos) es del servidor.

Estructura razonable (la primera fila de la tabla de "La dosis correcta"): un componente de página, un servicio de datos con `HttpClient` y la caché de `CacheServidor` (n5-02), y las validaciones en el esquema del formulario. Cuando aparezca una regla con peso (por ejemplo, "un producto con pedidos abiertos no se borra" visible en la interfaz), esa regla se extrae como una función pura en un archivo de dominio, sin crear las demás capas: la estructura crece con las reglas, no antes.

Contraste con el asistente de solicitud de este taller: ahí sí hay pasos condicionales, una cuota que se calcula en pantalla, reglas del cliente que el servidor repite y dos orígenes de datos. Es la diferencia entre "lógica de cliente moderada a compleja" y "pantalla CRUD".
