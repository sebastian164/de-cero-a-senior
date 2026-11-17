# Política de pruebas del equipo

Una página. Se revisa cada seis meses, con los datos de errores en producción en la mano.

## Qué se prueba, y a qué nivel

| Nivel | Qué | Cuándo corre |
|---|---|---|
| Unitarias, sin dobles | Reglas de negocio, cálculos, límites: lo que vive en funciones puras | En cada guardado y en cada cambio |
| De caso de uso, con dobles hechos a mano o falsos en memoria | La orquestación: qué se registra, qué se rechaza | En cada cambio |
| De integración | Cada adaptador contra la cosa real (base de datos, cola, servicio externo), con contenedores | En cada pull request |
| De API (Karate) | El contrato del servicio visto desde fuera | Humo en cada cambio; completa antes de fusionar |
| Propiedades | Funciones con entradas ricas: análisis de texto, cálculos con muchas combinaciones | Junto a las unitarias |

Todo error que llega a producción se corrige empezando por una prueba que lo reproduce y se ve fallar.

## Qué se mide

- **Mutantes que sobreviven en el código que cambió**, con PIT sobre los archivos tocados en el pull request.
  Quien revisa mira cada superviviente: o se mata, o se explica por qué es equivalente.
- **Pruebas que fallan a veces.** Cada una se arregla o se borra en la semana. No se marca como "inestable".
- **Errores que llegan a producción** y **tiempo desde que se fusiona hasta que se despliega con confianza.**
  Son los dos números que dicen si las pruebas sirven.
- **Lo que tarda la suite.** Si las unitarias pasan de un minuto, se investiga por qué.

## Qué NO se usa como objetivo

- **El porcentaje de cobertura.** Se mira para encontrar código que ninguna prueba ejecuta, nunca como meta.
  En nuestro propio experimento, una prueba sin una sola comprobación llegó al 100 % y dejó vivos 7 de 17 mutantes.
- **El porcentaje de mutantes muertos del proyecto entero.** Hay mutantes equivalentes que no se pueden matar,
  y perseguir el 100 % lleva a pruebas de casos imposibles.
- **El número de pruebas.**

## Qué no se hace

- Simular tipos que no son nuestros: se envuelven en un puerto propio y se dobla el puerto.
- Pruebas que dependen de la fecha u hora reales, del orden o de otras pruebas.
- Comprobar el orden de las llamadas internas, salvo cuando la llamada es el resultado (un cobro, un correo).
- Ignorar pruebas "temporalmente".

## Dónde no se exige TDD

Exploraciones (se tiran y se rehacen con pruebas), maquetación visual, código que solo conecta piezas
(basta una prueba de integración) y guiones de un solo uso. Quien decide no usar TDD lo dice en el pull request.
