# Taller integrador de arquitectura

Este taller se evalúa por las decisiones y su justificación más que por el código.

## El encargo

Una empresa de mensajería urbana quiere una plataforma para gestionar envíos dentro de una ciudad.

**Funcionalidad**

- Los clientes (personas y comercios) crean solicitudes de envío: origen, destino, tamaño del paquete, franja horaria.
- El sistema cotiza según distancia, tamaño, franja y tipo de cliente. Los comercios tienen tarifas negociadas.
- Se asigna un mensajero disponible cercano. El mensajero acepta o rechaza desde una app móvil.
- El envío pasa por estados: solicitado, asignado, recogido, en ruta, entregado, fallido, cancelado.
- El cliente sigue el envío en un mapa.
- Al entregar se cobra al cliente y se liquida al mensajero, semanalmente.
- Los comercios integran sus tiendas por API.
- Administración necesita reportes de operación y de facturación.

**Contexto**

- Equipo: 7 desarrolladores, uno de ellos tú, como responsable técnico.
- Lanzamiento en 4 meses en una ciudad; expansión a tres ciudades en un año si funciona.
- Volumen inicial: 2.000 envíos diarios. Pico esperado en diciembre: cinco veces más.
- El pago se hace con una pasarela externa. Los mapas, con un proveedor externo.
- Presupuesto de infraestructura limitado.

## Entregables

### 1. Atributos de calidad

Ordena los cinco más importantes para este sistema y justifica. Para cada uno, da un escenario medible ("con 500 envíos simultáneos, la cotización responde en menos de…").

### 2. Diseño estratégico

- Glosario del dominio (al menos quince términos).
- Contextos delimitados y su mapa.
- Clasificación de subdominios: núcleo, soporte, genérico. ¿Qué comprarías en lugar de construir?

### 3. Arquitectura

- ¿Monolito, monolito modular o microservicios? Decisión para el lanzamiento y para el año siguiente, con las condiciones que harían cambiarla.
- Módulos o servicios, con responsabilidad, API pública y datos propios de cada uno.
- Arquitectura interna de cada módulo. No tienen que ser todos iguales: justifica dónde vale la pena un hexágono y dónde basta un CRUD en capas.
- Comunicación entre módulos: qué es síncrono, qué va por eventos y por qué.
- Diagrama de contexto y de contenedores (texto, Mermaid o imagen).

### 4. Decisiones difíciles

Responde cada una con su alternativa descartada:

1. ¿Cómo garantizas que un mensajero no reciba dos envíos incompatibles asignados a la vez?
2. ¿Qué pasa si se registra la entrega pero el cobro falla?
3. ¿Cómo se actualiza el mapa del cliente? ¿Con qué frecuencia y por qué medio?
4. ¿Cómo aíslas al sistema de una caída del proveedor de mapas?
5. ¿Cómo evitas que los reportes de administración afecten la operación?
6. ¿Cómo versionas la API de comercios?

### 5. Tres ADR

Escribe tres registros de decisión de arquitectura con este formato: contexto, decisión, alternativas consideradas, consecuencias positivas y negativas.

### 6. Código

Implementa en Java puro el módulo que consideres el núcleo del negocio, con su dominio, sus casos de uso y sus puertos. Con pruebas de las reglas en `Main` o con JUnit.

### 7. Plan y riesgos

- Qué construyes en cada uno de los cuatro meses.
- Tres riesgos técnicos principales y cómo los mitigas.
- Qué dejas deliberadamente fuera del lanzamiento.

### 8. Defensa

Imagina que un arquitecto con más experiencia revisa tu propuesta. Escribe las tres objeciones más fuertes que podría hacer y tu respuesta a cada una.

## Entrega

Rama `taller/n5-12-integrador`. Su revisión cierra el Nivel 5 y la parte del curso que especificaste al detalle.

Siguiente: [Nivel 6 — Java avanzado](../../nivel-6-java-avanzado/).
