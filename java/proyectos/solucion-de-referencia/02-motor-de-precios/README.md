# Proyecto 2: motor de precios y promociones, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu historial de commits.

Enunciado: [sección 2 de proyectos/README.md](../../README.md#2-motor-de-precios-y-promociones). Java puro, una biblioteca sin interfaz.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Prueba que lo demuestra |
|---|---|---|
| Productos sueltos y combos que pueden contener combos | `Modelo.java` (`Item`, `Producto`, `Combo`) | `combos_anidados_cuentan_sus_productos` |
| Promociones combinables: porcentaje, valor fijo, 2x1, volumen, cupón | `Promocion.java` (`Porcentaje`, `ValorFijo`, `DosPorUno`, `Volumen`, `Cupon`) | `dos_por_uno_y_volumen_se_acumulan_y_se_explican` |
| Envío gratis sobre un monto | `Motor.calcular` | `impuestos_por_categoria_y_envio_gratis_sobre_el_monto` |
| Cuáles se acumulan y cuáles son excluyentes | `Promocion.grupo()`: dentro de un grupo solo aplica la que más descuenta | `promociones_excluyentes_aplica_solo_la_mejor` |
| En qué orden se aplican, definido en un solo lugar | la lista de promociones que recibe el constructor de `Motor` | `el_orden_de_la_lista_cambia_el_resultado_y_solo_se_define_ahi` |
| Impuestos por categoría | `Motor.calcular` (el descuento se reparte proporcionalmente entre categorías) | `el_impuesto_de_un_combo_se_calcula_sobre_su_precio_repartido_no_sobre_el_de_lista` |
| El resultado explica cómo se llegó al precio | `Resultado.explicacion()` | `dos_por_uno_y_volumen_se_acumulan_y_se_explican` |
| Agregar una promoción es agregar una clase | implementa `Promocion` | `agregar_una_promocion_es_agregar_una_clase` |

**Qué no cubre:** la extensión (reglas de promoción definidas como datos y cargadas de un archivo).

## Cómo ejecutarla

Requiere JDK 21 y Maven. No necesita Docker.

```bash
mvn clean verify
java -cp target/classes precios.Demo
```

`Demo` calcula un carrito con un combo, un 2x1 en café y un cupón del 10 %, e imprime la explicación (subtotal, cada descuento con su motivo, envío, impuestos y total). Para usar el motor en tu código:

```java
Motor motor = new Motor(List.of(new DosPorUno("2x1 en café", "Café")), Map.of("alimentos", d("0.05")), d("5.00"), d("100.00"));
Resultado r = motor.calcular(new Carrito(List.of(new Linea(cafe, 2))));
System.out.println(r.explicacion());
```

## Decisiones de diseño

Están en [DECISIONES.md](DECISIONES.md).
