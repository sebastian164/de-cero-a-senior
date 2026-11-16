# Taller — Herencia: figuras (solución de referencia)

Carpetas: `1-duplicado/` (punto 1), `2-jerarquia/` (puntos 2 y 3, con `Pruebas`: 7 pruebas, 0 fallos), `properties/` (punto 5, prototipo ejecutable).
Cada una: `javac -d out *.java && java -cp out Main`.

## 1. Con duplicación

`1-duplicado/Main.java`: `Circulo`, `Rectangulo` y `Triangulo` sin relación; las tres repiten el mismo `describir()` con `String.format`. Cambiar el formato (por ejemplo, tres decimales) obliga a tocar tres archivos.

## 2. Jerarquía

- En `Figura` (abstracta): el nombre, los métodos abstractos `area()` y `perimetro()`, y `describir()`/`descripcion()` como métodos `final` (plantilla). Es la parte idéntica y es la que no debe variar.
- No va en la superclase: los datos de cada figura (radio, ancho/alto, base/altura/lados) ni las fórmulas. Tampoco un "ancho" genérico: un círculo no tiene ancho.
- Las subclases son `final`: nadie más hereda de ellas sin una decisión explícita.
- Se usa `Locale.ROOT` en el formato para que "dos decimales" use punto en cualquier máquina; con el locale por defecto la salida cambia según el sistema operativo (por ejemplo `3,14`).

## 3. Mayor área sin conocer los tipos

`Figuras.mayorArea(List<? extends Figura>)` recorre la lista y compara `area()`. Salida real: `Mayor área -> Rectángulo: área 6.00, perímetro 10.00`.

## 4. `Cuadrado extends Rectangulo` con `setAncho` y `setAlto`

Un `Cuadrado` debe mantener `ancho == alto`. Para eso `setAncho` también cambia el alto y viceversa. Quien recibe un `Rectangulo` y hace `r.setAncho(4); r.setAlto(5);` espera área 20; con un cuadrado obtiene 25, porque el segundo setter pisó el primero. Peor: si `Cuadrado` no sobrescribe los setters, se puede dejar un "cuadrado" de 4 x 5. Cualquier código escrito para rectángulos (escalar solo el ancho, ajustar a una proporción) se rompe al recibir un cuadrado. El "es un" de la geometría no se cumple en el comportamiento con mutación. Es el caso que se desarrolla en LSP.

## 5. `java.util.Properties` hereda de `Hashtable<Object,Object>`

`Properties` debería ser un mapa de texto a texto, pero al heredar de `Hashtable<Object,Object>` expone `put(Object, Object)`. Se puede guardar un valor que no es `String` y el compilador no lo impide. Después, la API propia de `Properties` lo ignora o explota. El prototipo `properties/Main.java` lo demuestra (salida real en JDK 21):

```
getProperty("timeout") = null
get("timeout") = 30
store: ClassCastException
```

`getProperty` devuelve `null` aunque la clave existe, y `store` (guardar a archivo) falla en ejecución. La subclase heredó operaciones que rompen su propio contrato: debió usar composición (tener un mapa interno de `String` a `String`) en lugar de heredar. La documentación del JDK lo reconoce y desaconseja `put` y `putAll`.
