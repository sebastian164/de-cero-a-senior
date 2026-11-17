# Taller nivel-02/02: OCP. Decisiones

> **Dónde está el código.** Vive en la aplicación de referencia, `react-next/solucion-de-referencia-app/` (desde la raíz del curso). Los archivos sin ruta (`taller.test.tsx`, `despues/…`, `ci/…`, etc.) están en `react-next/solucion-de-referencia-app/talleres/react-next/nivel-02-solid/02-ocp/`; las rutas que empiezan por `src/`, `proyectos/`, `e2e/` o `scripts/` cuelgan de la raíz de esa aplicación. Los datos de `mediciones/` y las capturas de `capturas/` se generan al ejecutar las pruebas de navegador.

Pruebas: `taller.test.tsx`. Código: `alerta-antes.tsx` (partida, mal a propósito), `alerta.tsx`, `tarjeta.tsx`, `usos.tsx`, `notificaciones.tsx`, y `fallo-tipo-sin-vista/` (debe fallar al compilar).

## Punto 1. Seis booleanos a una variante y huecos

`AlertaAntes` aceptaba `exito`, `error`, `advertencia`, `conIcono`, `descartable` y `compacta`: seis booleanos son 64 combinaciones, de las cuales varias no tienen sentido. La prueba "la versión de partida permitía estados imposibles"
pasa `exito` y `error` a la vez y comprueba que **gana `error` en silencio** (el orden de los `?:` decide). `Alerta` usa una unión `variante: 'info' | 'exito' | 'advertencia' | 'error'` (los cuatro estados excluyentes no se pueden combinar)
y dos huecos (`icono`, `accion`) en lugar de `conIcono` y `descartable`. Además deduce el rol de la variante (`error` y `advertencia` son `alert`; el resto, `status`) y reenvía los atributos nativos del `div`.

## Punto 4. El mapa de notificaciones y agregar un tipo

`VISTAS: Record<Notificacion['tipo'], ComponentType>` obliga a registrar una vista por cada tipo. Al agregar `envio`, solo se escribe su vista y la entrada del mapa; el componente que decide qué dibujar no cambia (prueba: "el tipo de notificación agregado tiene su vista").
Si se agrega el tipo a la unión y se olvida la vista, **no compila**: `fallo-tipo-sin-vista/` da `TS2741: Property 'envio' is missing in type … but required in type 'Record<…>'`. `scripts/fallos-de-compilacion.sh` lo ejecuta y exige que falle.

## Punto 5. Un caso donde una simple propiedad `variante` era mejor

`Boton` con `variante: 'primario' | 'secundario' | 'peligro'`. Las tres variantes solo cambian la clase CSS; el comportamiento y la estructura son idénticos. Convertirlas en `BotonPrimario`, `BotonPeligro`… o en huecos
añadiría tres componentes que duplican el contrato del botón (y las oportunidades de olvidar `type`, `disabled` o la referencia, justo lo que enseña LSP). Se componen los huecos cuando cambia la *estructura* (icono, acción), y una propiedad cuando cambia solo el *aspecto*.
La misma `Alerta` lo combina: `variante` para el aspecto, huecos para la estructura.
