# Talleres de Docker y Kubernetes

Ruta práctica y común a las tres rutas del curso. Sirve igual si vienes de [Java](../java/), de [Angular](../angular/) o de [React y Next.js](../react-next/): aquí se aprende a empaquetar una aplicación, conectarla con otras y operarla, sea cual sea el lenguaje.

Son talleres. Cada sesión explica lo necesario y deja la mayor parte del tiempo para que lo hagas tú, con criterios de aceptación que puedes comprobar solo.

## Orden

Primero Docker completo, después Kubernetes. Los talleres de Kubernetes usan las imágenes que construyes en los de Docker.

### [Docker](docker/)

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Contenedores desde cero](docker/01-contenedores-desde-cero/) | Arrancar, inspeccionar y detener contenedores |
| 02 | [Tu primera imagen](docker/02-tu-primera-imagen/) | Escribir un Dockerfile y entender las capas |
| 03 | [Imágenes eficientes y seguras](docker/03-imagenes-eficientes-y-seguras/) | Varias etapas, usuario sin privilegios, salud |
| 04 | [Datos y redes](docker/04-datos-y-redes/) | Conservar datos y conectar contenedores por nombre |
| 05 | [Docker Compose](docker/05-docker-compose/) | Levantar un sistema completo con una orden |
| 06 | [De la imagen a producción](docker/06-de-la-imagen-a-produccion/) | Versionar, publicar, analizar y automatizar |

### [Kubernetes](kubernetes/)

| # | Sesión | Qué terminas sabiendo hacer |
|---|---|---|
| 01 | [Clúster local y primer pod](kubernetes/01-cluster-local-y-primer-pod/) | Levantar un clúster y leer lo que te dice |
| 02 | [Deployments y Services](kubernetes/02-deployments-y-services/) | Varias copias, nombre estable, actualizaciones |
| 03 | [Configuración y secretos](kubernetes/03-configuracion-y-secretos/) | Una imagen, varios ambientes |
| 04 | [Sondas, recursos y apagado ordenado](kubernetes/04-sondas-recursos-y-apagado/) | Desplegar sin que el usuario vea errores |
| 05 | [Tráfico de entrada: Gateway API](kubernetes/05-trafico-de-entrada/) | Publicar por nombre de host y hacer un canario |
| 06 | [Almacenamiento y estado](kubernetes/06-almacenamiento-y-estado/) | Volúmenes, StatefulSet y respaldos |
| 07 | [Escalado y actualizaciones](kubernetes/07-escalado-y-actualizaciones/) | Autoescalado, despliegues seguros, mantenimiento de nodos |
| 08 | [Seguridad del pod y de la red](kubernetes/08-seguridad-del-pod-y-de-la-red/) | Endurecer pods y cerrar la red |
| 09 | [Diagnóstico: arreglar un despliegue roto](kubernetes/09-diagnostico/) | Encontrar la causa con método |
| 10 | [Kustomize y taller integrador](kubernetes/10-kustomize-y-taller-integrador/) | Entregar todo, reproducible y defendido |

## Cuándo hacerlos

| Si vienes de | Hazlos después de |
|---|---|
| Java | El [Nivel 5](../java/nivel-05-arquitectura/). Complementan con práctica las sesiones 02 y 03 del [Nivel 10](../java/nivel-10-cloud/) |
| Angular | El [Nivel 5](../angular/nivel-05-arquitectura/) |
| React y Next.js | El [Nivel 5](../react-next/nivel-05-arquitectura/) |

No hace falta nada de la nube ni tarjeta de crédito: todo corre en tu equipo.

## Qué necesitas

- Docker funcionando (Docker Desktop en macOS y Windows; Docker Engine en Linux).
- Para la parte de Kubernetes: `kubectl` y una herramienta de clúster local. El curso usa [kind](https://kind.sigs.k8s.io/).
- Un JDK 21 si quieres ejecutar la aplicación de práctica sin contenedores. No es obligatorio: los talleres la compilan dentro de la imagen.
- Unos 8 GB de memoria libres para el clúster de tres nodos.

## La aplicación de práctica

Todos los talleres usan [Bitácora](app/): una API mínima en Java y una página estática. Es pequeña a propósito y tiene un defecto de diseño deliberado (guarda las notas en un archivo local) que varias sesiones aprovechan.

## Cómo es cada sesión

Sigue el ciclo de la [metodología](../METODOLOGIA.md) con los pasos adaptados a infraestructura:

1. **Idea:** una analogía.
2. **Lo esencial:** el concepto con sus términos.
3. **Ejemplo:** una configuración que funciona pero está mal, su análisis y la versión corregida.
4. **Errores comunes.**
5. **Taller:** objetivo, pasos, criterios de aceptación y entrega.
6. **En el trabajo real.**
7. **Preguntas senior.**

Los ejemplos usan una aplicación distinta a la de los talleres. Es a propósito: el ejemplo enseña la idea, y aplicarla a Bitácora es tu trabajo.

## Cómo entregar

Igual que en el resto del curso: una rama por taller (`taller/docker-01`, `taller/k8s-04`…), tu trabajo en `taller/solucion/` dentro de la carpeta de la sesión, un `ANALISIS.md` con las órdenes, las salidas y tus explicaciones, y un pull request contra `main`. No hay soluciones de referencia publicadas: la revisión se hace sobre tu entrega.

## Estado del código

| Qué | Cómo se comprobó al preparar el curso |
|---|---|
| API de Bitácora (`app/api/Servidor.java`) | Compilada y ejecutada con JDK 21; se probaron todas sus rutas, el arranque lento y el apagado con `SIGTERM` |
| Manifiestos de Kubernetes con tipos estándar | Validados contra los esquemas de Kubernetes 1.37 |
| Archivos de Compose | Validados con `docker compose config` |
| Dockerfile, manifiestos de Gateway API, Kustomize y órdenes de terminal | Revisados, pero **no ejecutados**: no había motor de Docker ni clúster disponibles |

Las etiquetas de imagen de los ejemplos (`nginx:1.28-alpine`, `postgres:17`, `node:24-alpine`) existían al escribir esto. Cuando hagas el taller, usa la versión vigente y fíjala.

Las herramientas de este tema cambian rápido. Si una orden de instalación del curso no coincide con la documentación oficial de la herramienta, manda la documentación.
