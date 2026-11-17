# Sagas 03: La saga coreografiada: solución de referencia

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura.

La misma saga de compra sin orquestador: cada servicio reacciona a los eventos de los demás.

Análisis y decisiones: [ANALISIS.md](ANALISIS.md).

## Requisitos

JDK 21 y Maven.

## Cómo ejecutarla y probarla

```bash
mvn test
java -cp target/classes tienda.Main   # imprime la traza de los cuatro caminos
```
