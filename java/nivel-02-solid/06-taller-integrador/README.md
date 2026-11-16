# Taller integrador de SOLID

Los cinco principios no se aplican por separado. Este taller obliga a usarlos juntos y, sobre todo, a decidir cuándo no usarlos.

## Cómo se relacionan

| Si violas… | Sueles terminar violando… | Porque… |
|---|---|---|
| SRP | OCP | Una clase con varias responsabilidades se modifica por cualquiera de ellas. |
| ISP | LSP | Una interfaz gorda obliga a implementar métodos que no se pueden cumplir. |
| LSP | OCP | El cliente necesita `instanceof` para tratar los casos especiales. |
| DIP | OCP | Sin abstracción no hay punto de extensión. |

Y todos apuntan a lo mismo que el Nivel 1: alta cohesión y bajo acoplamiento.

## El sistema

Una clínica veterinaria tiene este requisito inicial, y un único archivo que lo resuelve "como sea":

- Registra citas: mascota, dueño, tipo (consulta, vacunación, cirugía) y fecha.
- El precio depende del tipo: consulta 60.000, vacunación 45.000, cirugía 400.000.
- Las cirugías requieren que la mascota tenga una consulta previa registrada.
- Al registrar una cita se guarda en un archivo de texto y se envía un mensaje al dueño por correo.
- Al final del día se imprime un reporte con las citas y el total facturado.

## Parte 1 — Versión sin diseño

Escríbela tú, en una sola clase, en menos de una hora. No la mejores. Guárdala en `solucion/1-sin-diseno/`.

## Parte 2 — Diagnóstico

En `ANALISIS.md`, señala cada violación de cada principio con la línea donde ocurre. Si un principio no se viola, dilo y explica por qué.

## Parte 3 — Rediseño

Guárdalo en `solucion/2-rediseno/`. Requisitos:

- Ninguna regla de negocio puede depender de archivos ni de correo.
- Debe ser posible verificar las reglas desde `Main` con dobles en memoria.
- Dibuja (texto o imagen) las clases y la dirección de sus dependencias.

## Parte 4 — La prueba del cambio

Aplica estos cambios sobre tu rediseño y registra qué archivos tocaste en cada uno:

1. Nuevo tipo de cita: peluquería, 35.000, sin requisitos.
2. Los dueños pueden elegir recibir el aviso por WhatsApp en lugar de correo.
3. Las citas pasan a guardarse en una base de datos (simúlala en memoria).
4. Las vacunaciones requieren que hayan pasado más de 30 días desde la última vacuna de la mascota.
5. Descuento del 10 % para dueños con más de cinco citas en el año.

Si algún cambio te obligó a modificar más de dos clases existentes, explica qué supuesto de tu diseño falló.

## Parte 5 — Contra el sobrediseño

1. Señala una abstracción de tu rediseño que hoy tiene una sola implementación. Defiéndela o elimínala.
2. Indica un lugar donde decidiste **no** aplicar un principio y por qué.
3. Si este sistema fuera a usarse en una sola clínica durante seis meses y luego descartarse, ¿qué versión entregarías? Justifica.

## Entrega

Rama `taller/06-solid-integrador`. Este taller cierra el Nivel 2: su revisión decide si se avanza a patrones.

Siguiente: [Nivel 3 — Patrones de diseño](../../nivel-03-patrones/).

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
