# Proyecto 1: gestor de biblioteca en consola, solución de referencia

> **Intenta el proyecto antes de leer esto.** Ver la solución primero convierte el proyecto en lectura y no en práctica. Es *una* solución mínima y verificada, no *la* solución: la revisión de tu entrega mira tu diseño, tus decisiones y tu historial de commits.

Enunciado: [sección 1 de proyectos/README.md](../../README.md#1-gestor-de-biblioteca-en-consola). Java puro, sin frameworks.

## Qué requisitos cubre y dónde

| Requisito del enunciado | Dónde está | Prueba que lo demuestra |
|---|---|---|
| Catálogo de libros con varios ejemplares, socios, préstamos y devoluciones | `src/main/java/biblioteca/Biblioteca.java` (`agregarLibro`, `registrarSocio`, `prestar`, `devolver`) | `BibliotecaTest` |
| Máximo tres préstamos por socio | `Socio.validarPuedePedir` | `maximo_tres_prestamos` |
| No se presta a quien tiene multas | `Socio.validarPuedePedir` | `multa_por_retraso_bloquea_nuevos_prestamos_hasta_pagarla` |
| Multa por día de retraso | `Prestamo.multaAl` (14 días de plazo, 50 centavos por día) | la misma prueba, con un reloj controlado |
| Lista de espera para libros agotados | `Biblioteca.prestar` y `Biblioteca.devolver` (entrega el ejemplar al primero de la lista que pueda recibirlo) | `libro_agotado_va_a_lista_de_espera_y_se_entrega_al_devolver` |
| Los datos sobreviven al cierre del programa | `AlmacenArchivo.java` (archivo de texto, una línea por registro) | `los_datos_sobreviven_al_cierre_del_programa` |
| Objetos que protegen sus reglas | `Socio` y `Prestamo` son quienes saben si pueden pedir, cuándo vencen y cuánto se debe | `BibliotecaTest` |
| Ausencia de estado global | no hay campos estáticos mutables; el reloj y el almacén entran por el constructor de `Biblioteca` | `BibliotecaTest` usa un `Clock` propio |
| Separación entre reglas, almacenamiento y consola | `Biblioteca` (reglas), `Almacen` (interfaz) con `AlmacenArchivo`, `Consola` (texto) | `cambiar_el_almacenamiento_no_toca_las_reglas` y `la_consola_traduce_comandos` |

**Qué no cubre:** la extensión (revistas con otras reglas de préstamo, sin modificar las clases existentes). Es tu reto si quieres dar el paso siguiente.

## Cómo ejecutarla

Requiere JDK 21 y Maven. No necesita Docker ni nada más.

```bash
mvn clean verify
```

Compila y ejecuta las pruebas. Para usar la consola (los datos se guardan en el archivo que pases, aquí `biblioteca.tsv`):

```bash
java -cp target/classes biblioteca.Consola biblioteca.tsv
```

Comandos, uno por línea:

| Comando | Qué hace |
|---|---|
| `libro <isbn> <título> <ejemplares>` | agrega ejemplares de un libro (el título es una sola palabra) |
| `socio <id> <nombre>` | registra un socio |
| `prestar <isbn> <socio>` | presta, o pone en lista de espera si no quedan ejemplares |
| `devolver <isbn> <socio>` | devuelve, imprime la multa y pasa el ejemplar al primero en espera |
| `pagar <socio>` | paga la multa |
| `salir` | termina |

Prueba rápida sin teclado:

```bash
printf 'libro L1 Dune 1\nsocio ana Ana\nsocio beto Beto\nprestar L1 ana\nprestar L1 beto\nsalir\n' | java -cp target/classes biblioteca.Consola biblioteca.tsv
```

Imprime `ok`, `ok`, `ok`, `PRESTADO` y `EN_LISTA_DE_ESPERA`. Si ahora ejecutas solo `prestar L1 beto`, responde `NO: Ya está en la lista de espera`: el programa se cerró, pero los datos viven en `biblioteca.tsv`. Bórralo para empezar de cero.

## Decisiones de diseño

Están en [DECISIONES.md](DECISIONES.md), con las alternativas que se descartaron.
