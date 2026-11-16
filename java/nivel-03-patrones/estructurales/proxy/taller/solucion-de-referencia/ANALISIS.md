# Proxy — análisis

1. **Caché con clave = año**: basta con que el resultado dependa del usuario. Escenario: cada gerente ve solo las ventas de su región. `ana` (Boyacá) consulta 2025 y llena la caché; `luis` (Antioquia) consulta 2025 y recibe las cifras de Boyacá: fuga de información entre usuarios. Corrección: `ReportesConCache` usa `record Clave(usuario, anio)`; regla general, la clave incluye todo lo que afecta el resultado (o la caché se pone donde el resultado ya no depende del usuario). Pruebas: `cachePorAnioFiltraDatosEntreUsuarios` y `cacheConClaveCompletaEsCorrecta`.

2. **Orden invertido (caché por fuera, permisos por dentro)**: con la caché del ejemplo, una vez que `ana` llenó la caché de 2025, un usuario **sin permiso** recibe el reporte: la caché responde sin llamar al objeto envuelto y el control de permisos nunca se ejecuta. Demostrado en `Demostraciones.intrusoConCacheFuera()` (devuelve el dato de Boyacá). Con la clave corregida el intruso no acierta en la caché y sí llega a permisos, pero el orden sigue siendo frágil: los controles de acceso van por fuera de la caché.

3. **Carga diferida**: `ImagenDiferida` crea `ImagenReal` (que "carga del disco" en su constructor) solo en el primer `mostrar()`. Se verifica que el contador de cargas es 0 tras crear el proxy y 1 tras dos `mostrar()`. (No es seguro entre hilos; para eso, `synchronized` o un *holder*.)

4. **Proxy dinámico** `Cronometro.envolver(interfaz, real, salida)` con `java.lang.reflect.Proxy`: sirve para `Reportes`, `Runnable` y hasta `List`. Desenvuelve `InvocationTargetException` para propagar la excepción original (`proxyDinamicoPropagaLaExcepcionOriginal`).

5. **Autoinvocación con `@Transactional`**:
   ```java
   @Service
   class ServicioPedidos {
       public void procesarLote(List<Pedido> pedidos) { pedidos.forEach(this::guardar); }
       @Transactional public void guardar(Pedido p) { ... }
   }
   ```
   `procesarLote` llama a `this.guardar(...)`: `this` es el objeto real, no el proxy, así que `guardar` corre **sin transacción**. Correcciones: (a) mover `guardar` a otro bean e inyectarlo, de modo que la llamada pase por su proxy; (b) inyectar el propio proxy (`@Lazy ServicioPedidos self` o `ObjectProvider`) y llamar `self.guardar(...)`; (c) usar `TransactionTemplate` de forma explícita. Esto se comprobó con un proyecto Spring de pruebas (`ProxyAutoinvocacionTest`): la llamada externa tiene transacción activa, la autoinvocación no, y las correcciones (a) y (b) sí la tienen.
