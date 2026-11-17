# Taller frontend-senior/01: diseño de sistemas frontend. Qué entrega y cómo se comprobó

El entregable es un documento de diseño, [`DISENO.md`](../../../../react-next/solucion-de-referencia-app/talleres/frontend-senior/01-diseno-de-sistemas-frontend/DISENO.md), con tres problemas clásicos: muro de noticias con desplazamiento infinito, chat y carga de archivos grandes. Cada problema sigue el método de la sesión: requisitos y fuera de alcance, arquitectura (con diagrama), modelo de datos y contrato, clasificación del estado, árbol de componentes, cinco decisiones con su alternativa y modos de fallo.

## Qué comprueba la prueba (`taller.test.ts`)

| Punto del taller | Cómo se comprueba |
|---|---|
| Tres problemas | El documento tiene exactamente tres problemas. |
| Todas las secciones pedidas | Cada problema trae las siete secciones por su nombre. |
| Diagrama y decisiones | Cada problema tiene un diagrama y cinco decisiones, cada una con su alternativa. |
| Implementar uno de los tres | El chat está en `chat.ts` y se ejecuta en pruebas. |

## El chat implementado

`chat.ts` es una función pura sobre un estado: el envío optimista aparece al instante y se confirma cuando llega la secuencia del servidor. Las pruebas cubren cuatro comportamientos:

1. **Envío optimista:** el mensaje aparece pendiente y pasa a confirmado con su número de secuencia.
2. **Desorden y repetidos:** lo que llega desordenado queda ordenado por secuencia, y un repetido no se duplica.
3. **Pendientes al final:** un mensaje pendiente se queda al final hasta que llega su secuencia, aunque lleguen mensajes ajenos antes.
4. **Fallo, reintento y reconexión:** al reconectar se pide lo perdido, se reenvía lo pendiente y la idempotencia del servidor evita duplicados.

## Qué conviene aprender de la comparación

Un documento de diseño no se ejecuta, pero se puede exigir que esté completo. La prueba comprueba la forma (secciones, diagrama, número de decisiones), no si las decisiones son buenas: eso lo decides tú comparando tus alternativas con las de este documento.
