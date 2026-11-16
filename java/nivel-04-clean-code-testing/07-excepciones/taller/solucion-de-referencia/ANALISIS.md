# Taller 07 — Manejo de excepciones: solución

Código: `Main.java` (`java Main.java`). Incluye el `Importador` original sin cambios (`ImportadorOriginal`)
para comparar, y 16 comprobaciones que terminan con código 1 si alguna falla. Todas pasan.

## 1. Lo que oculta el código original

| Qué oculta | Problema real que causaría |
|---|---|
| `catch (Exception e)` vacío | Se pierde **cualquier** excepción, no solo las de formato: también un `NullPointerException` causado por un error de programación. Nadie se entera |
| No dice qué líneas fallaron | De 10.000 productos se importan 9.200 y nadie sabe cuáles faltan; se descubre cuando un cliente no encuentra el producto |
| No dice por qué | Aunque se supiera qué línea falló, no se sabe si era el precio, el separador o una línea vacía; hay que reproducirlo a mano |
| No da el número de línea | Para corregir el archivo hay que buscar a ojo |
| `return null` cuando nada se importa | Quien llama recibe `null` y falla con `NullPointerException` lejos del origen; y no puede distinguir "archivo vacío" de "todo falló" |
| `campos[1]` sin comprobar la longitud | "Monitor" lanza `ArrayIndexOutOfBoundsException`, que el `catch` genérico disfraza de "línea mala" |
| Campos de más (`"Base;1;2"`) | Se importan ignorando el tercer campo: un error de formato que pasa como válido |
| Precio negativo | Se importa sin queja |

Comprobado: el original devuelve `[Teclado=120000]` con la entrada del taller y `null` con `List.of("x")`.

## 2. Política elegida: reportar y continuar

Una línea inválida **no** detiene la importación. Se rechaza con su número y su motivo, y se sigue. Es la
política habitual para cargas masivas: el archivo bueno no se pierde por una línea mala, y el informe de
rechazos le dice al usuario exactamente qué corregir. **Ningún error se pierde:** cada línea de la entrada
termina en `importados` o en `rechazados` (se comprueba que la suma coincide con el total de líneas).

Lo que sí se lanza: una lista `null` (`NullPointerException` con el nombre del parámetro). Eso no es un
dato malo del archivo sino un error de programación de quien llama, y debe fallar de inmediato.

## 3. Resultado

`ResultadoImportacion(List<Producto> importados, List<Rechazo> rechazados)`, con
`Rechazo(numeroDeLinea, contenido, motivo)`. Salida con la entrada del taller:

```
importados: [Producto[nombre=Teclado, precio=120000]]
rechazados: 3
  línea 2 ("Mouse;abc"): el precio no es un número entero: "abc"
  línea 3 (""): la línea está vacía
  línea 4 ("Monitor"): se esperaban 2 campos (nombre;precio) y hay 1
```

## 4. Sin `null`

`importar` siempre devuelve un `ResultadoImportacion`; si nada se importa, `importados` es una lista vacía.
Las listas son copias inmodificables. Tampoco el método interno devuelve `null`: `motivoDeRechazo`
devuelve `Optional<String>`.

## 5. ¿Jerarquía de excepciones? No hizo falta

Se usó un **objeto resultado** y no excepciones porque una línea inválida es un **resultado esperado** de
importar un archivo hecho por personas, no una situación excepcional; y porque se quieren **todos** los
errores a la vez, no el primero (una excepción corta el flujo en la primera línea mala). La lección lo dice
en "Cuándo no usar excepciones": validaciones con varios errores a la vez.

La única excepción que se captura es `NumberFormatException`, la concreta y esperada de `Long.parseLong`, y
se convierte en un rechazo con contexto (el valor que no se pudo leer). No hay `catch` genéricos.

Si la política fuera "detener", sí tendría sentido una excepción propia con contexto, por ejemplo
`LineaInvalidaException(numeroDeLinea, contenido, motivo)` que extienda una `ImportacionException` no
comprobada; quien llama decidiría en la frontera qué hacer con ella.
