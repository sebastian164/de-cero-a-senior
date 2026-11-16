# Taller — Polimorfismo: transportes (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (15 pruebas, 0 fallos); `java sobrecarga/Main.java`.

## 1. Los `switch` reemplazados

`Transporte` (interfaz) con `costo`, `tiempoEstimadoHoras` y `puedeLlevar`; una clase por transporte (`Moto`, `Camion`, `Bicicleta`). Las cifras son las del código de inicio y `Main` imprime lo mismo que el original: `7000`, `224000`, `1`, `false`.

El código de inicio tenía un defecto oculto: `puedeLlevar("BICICLETA", ...)` caía en `default` y devolvía `false` siempre. Con la interfaz, la bicicleta está obligada a implementar `puedeLlevar`; se eligió un límite de 8 kg (supuesto, el enunciado no lo da). Los `default` que devolvían 0 (costo cero para un transporte mal escrito) desaparecen.

## 2. Dron (hasta 2 kg, hasta 10 km)

Archivos nuevos: `Dron.java`. Archivos existentes modificados: solo `Transportes.java`, para agregar `case "DRON"` en la fábrica. Ningún transporte existente ni `masBarato` cambió. Costo y tiempo del dron son supuestos (4.000 + 700 por km, 1 hora), porque el enunciado no los fija.

## 3. El más barato que puede llevarlo

`Transportes.masBarato(List<Transporte>, Envio)` filtra por `puedeLlevar` y toma el mínimo de `costo`; devuelve `Optional` porque puede que ninguno pueda. Salida real: 1 kg a 5 km → `BICICLETA` (3.500, más barato que el dron con 7.500); 300 kg a 120 km → `CAMION`.

## 4. Dónde quedó el `switch`

En `Transportes.desdeTexto`, la fábrica. Es aceptable porque es el borde del sistema: el texto llega de afuera (un formulario, un archivo) y en algún punto hay que traducirlo a un objeto. Es un solo `switch`, en un solo lugar, que solo crea objetos; el resto del programa trabaja con `Transporte`. Con el `switch` en cada operación, cada transporte nuevo tocaba tres métodos; ahora toca una línea. Además, un texto desconocido lanza excepción en lugar de devolver 0.

## 5. Sobrecarga que sorprende

`sobrecarga/Main.java`. Salida real:

```
tarifa por código de envío
tarifa general
[10, 30]
[30]
```

`tarifa(codigo)` con `Object codigo = "ENV-001"` llama a `tarifa(Object)` aunque el objeto sea un `String`: la sobrecarga se elige en compilación con el tipo estático. Y `List<Integer>.remove(1)` quita la posición 1, no el valor 1, porque `remove(int)` es más específico que `remove(Object)` para un literal `int`.
