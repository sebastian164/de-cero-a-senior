# 04 — Integración y entrega continuas

## Idea

En una línea de ensamblaje, cada estación revisa la pieza antes de pasarla a la siguiente. Un defecto se detecta en la estación donde se produjo, no cuando el carro ya está en el concesionario. Un *pipeline* es esa línea para el software: cada cambio pasa por las mismas revisiones automáticas antes de llegar a producción.

## Lo esencial

| Práctica | Qué es |
|---|---|
| **Integración continua (CI)** | Integrar cambios a la rama principal con frecuencia (al menos a diario), verificando cada uno con construcción y pruebas automáticas |
| **Entrega continua (CD)** | El software está siempre en estado desplegable; publicar es una decisión, no un proyecto |
| **Despliegue continuo** | Cada cambio que pasa el *pipeline* llega a producción sin intervención |

### Etapas típicas

```
commit → compilar → pruebas unitarias → análisis estático y de seguridad
       → construir la imagen → pruebas de integración → publicar la imagen
       → desplegar a pruebas → pruebas de humo → desplegar a producción
```

### Principios

- **Construir una vez, desplegar muchas.** El mismo artefacto (la misma imagen, identificada por su resumen o por el *commit*) recorre todos los ambientes. Lo que cambia es la configuración.
- **Rápido.** Si la retroalimentación tarda más de diez o quince minutos, la gente deja de esperarla.
- **La rama principal siempre está en verde.** Un *pipeline* roto se arregla antes que cualquier otra cosa.
- **Todo como código:** el *pipeline*, la infraestructura y la configuración viven en el repositorio.
- **Cambios pequeños.** Un despliegue de diez líneas es fácil de revisar y de revertir; uno de un mes es una apuesta.
- **Reversa preparada.** Saber volver atrás antes de avanzar.

### Estrategias de ramas

| Estrategia | Cómo | Conviene |
|---|---|---|
| Desarrollo basado en tronco | Ramas de horas o un par de días; integración constante; banderas de función | Equipos con buena automatización |
| Flujo de GitHub | Rama por cambio, *pull request*, fusión a principal | La mayoría de los equipos |
| Git Flow | Ramas de desarrollo, versión y corrección | Productos con versiones instaladas por clientes |

Las ramas de larga vida son deuda: cuanto más tardan en integrarse, más duelen.

### Métricas de entrega (DORA)

Frecuencia de despliegue, tiempo desde el *commit* hasta producción, porcentaje de despliegues que fallan y tiempo de recuperación. Los equipos de alto desempeño son mejores en velocidad **y** en estabilidad a la vez: no son objetivos opuestos.

## Ejemplo: GitHub Actions

```yaml
name: ci
on:
  push: { branches: [main] }
  pull_request:

jobs:
  construir:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21, cache: maven }
      - run: ./mvnw verify                       # compila y ejecuta todas las pruebas
      - name: Construir y publicar la imagen
        if: github.ref == 'refs/heads/main'
        run: |
          docker build -t registro.example/pedidos:${{ github.sha }} .
          docker push registro.example/pedidos:${{ github.sha }}

  desplegar:
    needs: construir
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: produccion                      # puede exigir aprobación manual
    steps:
      - run: kubectl set image deployment/pedidos pedidos=registro.example/pedidos:${{ github.sha }}
```

Las credenciales se obtienen de los secretos de la plataforma o, mejor, con identidad federada (OIDC), sin claves de larga duración.

## Errores comunes

- "Integración continua" con ramas que viven semanas.
- Reconstruir el artefacto para cada ambiente.
- Pruebas inestables que se reintentan hasta que pasan.
- *Pipelines* de 45 minutos.
- Pasos manuales no documentados para desplegar.
- Secretos en los archivos del *pipeline* o impresos en los registros.
- Desplegar los viernes en la tarde sin poder revertir.
- Migraciones de base no incluidas en el flujo.
- Saltarse el *pipeline* "solo por esta vez".

## Taller

1. Crea un *pipeline* para un proyecto Spring Boot: compilar, probar, analizar y construir la imagen en cada *pull request*.
2. Protege la rama principal: sin fusión si el *pipeline* falla o falta una revisión.
3. Agrega el despliegue automático a un entorno (el clúster local de la lección anterior u otro).
4. Incluye las migraciones de base de datos en el flujo.
5. Mide la duración y redúcela con caché y paralelismo.
6. Agrega una prueba de humo posterior al despliegue y la reversa automática si falla.
7. Calcula las cuatro métricas de entrega de un proyecto real.

Rama `taller/n10-04-ci-cd`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Diferencia entre entrega continua y despliegue continuo.
2. ¿Por qué "construir una vez, desplegar muchas"?
3. ¿Qué estrategia de ramas recomiendas y de qué depende?
4. ¿Cómo separas desplegar de liberar una función?
5. ¿Qué haces con una prueba inestable que bloquea el *pipeline*?
6. ¿Qué miden las métricas de entrega y cómo las mejorarías en tu equipo?
