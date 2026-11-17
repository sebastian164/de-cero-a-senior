# Criterio del equipo para elegir el peldaño (punto 6)

Subir un peldaño solo cuando el anterior duele, y por una razón concreta:

1. **Señal en el componente** si solo lo usa ese componente (un desplegable, el texto de un campo).
2. **Entradas y salidas** si lo comparten un padre y sus hijos cercanos.
3. **URL** si debe sobrevivir a recargar, funcionar con el botón atrás o poder compartirse por enlace: filtros, página, pestaña, identificador. Regla: si te preguntarías "¿puedo mandar este estado por mensaje?", es URL.
4. **Servicio con señales por funcionalidad** si varios componentes de una funcionalidad lo comparten. Se provee en las rutas de la funcionalidad, no en `root`, salvo que sea de verdad global (sesión).
5. **Biblioteca (SignalStore)** solo cuando haya muchos almacenes con patrones repetidos (carga de entidades, estados de petición, persistencia) y el equipo acepte aprender su vocabulario. Con un solo almacén no aporta nada medible (ver `ANALISIS.md`).
6. **Almacén global con acciones y efectos** solo con flujos complejos y necesidad real de trazabilidad.

Aparte: **el estado de servidor no es un almacén de cliente.** Se guarda con clave, vigencia, deduplicación e invalidación (`CacheServidor`) o con los recursos del framework; nunca como una copia que nadie revalida.
