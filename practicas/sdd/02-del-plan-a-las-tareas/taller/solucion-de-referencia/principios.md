# Principios del proyecto

- **Pruebas.** Cada criterio de la especificación tiene una prueba de aceptación escrita por una persona antes del código. Las funciones puras se prueban sin levantar el servidor. Nada se da por terminado con una prueba en rojo.
- **Dependencias.** No se añade ninguna: Bitácora es un solo archivo y usa solo el JDK. Las pruebas de aceptación viven aparte, en Karate.
- **Compatibilidad.** Un cliente existente no debe notar el cambio: solo se añaden campos y parámetros opcionales.
- **Nombres.** En español, como el resto de la aplicación: `etiquetas`, `MAXIMO_ETIQUETAS`. Las respuestas de error son `{ "error": "<mensaje en español>" }`.
- **Revisión.** Cada tarea es un commit que nombra la tarea y los criterios que cumple, y se lee completo antes de pasar a la siguiente.
- **Fuera de alcance.** Lo que la especificación excluye no se implementa "por si acaso".
