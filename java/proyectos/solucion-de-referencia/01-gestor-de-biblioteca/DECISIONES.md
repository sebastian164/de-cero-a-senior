# Decisiones de diseño: gestor de biblioteca

**Un `Clock` inyectado en lugar de `LocalDate.now()`.** Las multas dependen de fechas; con el reloj entrando por el constructor, las pruebas avanzan días sin esperar. Alternativa descartada: pasar la fecha en cada método, que ensucia todas las firmas.

**`Socio` y `Prestamo` son registros con comportamiento.** Quién puede pedir un libro y cuánto se debe son reglas del socio y del préstamo, no de quien los usa. `Biblioteca` orquesta, no decide. Alternativa descartada: reglas dentro de `Biblioteca` sobre datos "tontos"; funciona, pero cada regla nueva engorda una sola clase.

**`Almacen` es una interfaz de dos métodos (`cargar` y `guardar`) sobre un `Estado` completo.** Cambiar el archivo por otro almacenamiento solo exige otra implementación; las reglas no cambian (hay una prueba que lo demuestra con un almacén en memoria). Coste: se guarda todo en cada operación, aceptable para una biblioteca pequeña e inaceptable con miles de socios; allí se cambiaría por operaciones granulares.

**El formato del archivo es texto con tabuladores.** Se puede leer y corregir a mano, y no añade dependencias. Limitación conocida: un título con tabulador lo rompería, y el comando de consola solo admite títulos de una palabra.

**La lista de espera es estricta.** Si hay alguien esperando, un libro libre no se presta a un tercero que llega después: se entrega al primero de la lista que pueda recibirlo (quien tenga multas o ya tenga tres libros se salta).

**Multa en centavos enteros.** Evita errores de redondeo con decimales.
