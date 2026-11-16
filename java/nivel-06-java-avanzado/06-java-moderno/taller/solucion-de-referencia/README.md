# Solución de referencia: Java moderno

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Requisitos: JDK 21 y Maven 3.9 o superior.

## Ejecutarla y probarla

Desde esta carpeta:

```bash
mvn -B -ntp clean test
```

La prueba del punto 6 ejecuta `post21/Novedades.java` con un JDK posterior al 21. Indica dónde está con `-Djdk.reciente=<carpeta del JDK>`; sin esa opción busca en `/opt/homebrew/opt/openjdk` (Homebrew en macOS) y, si esa carpeta no existe, la prueba aparece como **omitida**. Ejemplos:

```bash
mvn -B -ntp clean test -Djdk.reciente=/usr/lib/jvm/jdk-25            # Linux
mvn -B -ntp clean test -Djdk.reciente=/opt/homebrew/opt/openjdk      # macOS con Homebrew
```

## Qué se hizo y por qué

Las decisiones, los resultados medidos y los hallazgos están en [ANALISIS.md](ANALISIS.md).
