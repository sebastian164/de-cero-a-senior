# 01 — Fundamentos de la nube

## Idea

Puedes tener tu propia planta eléctrica o conectarte a la red y pagar lo que consumes. La nube es conectarse a la red: cómputo, almacenamiento y servicios bajo demanda, sin comprar ni mantener los equipos.

## Lo esencial

### Modelos de servicio

| Modelo | Tú gestionas | El proveedor gestiona | Ejemplos |
|---|---|---|---|
| IaaS | Sistema operativo, ejecución, aplicación | Hardware, red, virtualización | EC2, Compute Engine, máquinas virtuales de Azure |
| Contenedores gestionados | Imagen y configuración | El clúster | EKS, GKE, AKS, Cloud Run, ECS |
| PaaS | Código | Plataforma y escalado | App Service, Elastic Beanstalk |
| FaaS / *serverless* | Funciones | Todo lo demás | Lambda, Cloud Functions |
| SaaS | Uso y configuración | Todo | Correo, CRM |

Cuanto más gestiona el proveedor, menos operas y menos control tienes. Regla práctica: usa el nivel más alto que cumpla tus requisitos.

### Responsabilidad compartida

El proveedor responde por la seguridad **de** la nube (centros de datos, hardware). Tú respondes por la seguridad **en** la nube: configuración, accesos, datos, parches de lo que administras. La mayoría de las filtraciones ocurren del lado del cliente: un almacenamiento público por error, una clave expuesta.

### Bloques básicos

| Bloque | Qué es |
|---|---|
| Región y zona de disponibilidad | Ubicación geográfica y centros de datos aislados dentro de ella. Desplegar en varias zonas da tolerancia a fallos |
| Cómputo | Máquinas virtuales, contenedores, funciones |
| Almacenamiento de objetos | Archivos con durabilidad altísima (S3, Blob Storage, Cloud Storage) |
| Bases de datos gestionadas | Respaldo, réplicas y parches a cargo del proveedor |
| Red | Red privada virtual, subredes públicas y privadas, balanceadores, DNS |
| Identidad y acceso (IAM) | Quién puede hacer qué sobre qué recurso |
| Mensajería gestionada | Colas y flujos de eventos |

### Principios de identidad y acceso

- **Mínimo privilegio:** cada identidad recibe solo los permisos que necesita.
- **Roles para las aplicaciones**, no claves de larga duración incrustadas.
- **Autenticación de varios factores** para personas.
- Nunca usar la cuenta raíz para el trabajo diario.

### Escalabilidad

- **Vertical:** una máquina más grande. Simple, con techo.
- **Horizontal:** más máquinas. Exige que la aplicación sea **sin estado**: la sesión y los archivos no pueden vivir en la instancia.
- **Elasticidad:** escalar automáticamente según la carga, hacia arriba y hacia abajo.

### Costos

Se paga por uso, y la factura sorprende. Hábitos mínimos: etiquetar recursos, alertas de presupuesto, apagar los entornos que no se usan, dimensionar según medición, y vigilar el tráfico de salida, que suele ser el costo oculto.

## Ejemplo: de un servidor a un despliegue tolerante a fallos

```
Antes:  [un servidor: aplicación + base de datos + archivos]

Después:
   Balanceador
     ├── instancia de la aplicación (zona A)   sin estado
     └── instancia de la aplicación (zona B)   sin estado
   Base de datos gestionada, con réplica en otra zona
   Almacenamiento de objetos para los archivos
   Caché gestionada para las sesiones
```

## Errores comunes

- Mover un servidor a la nube sin cambiar nada y esperar ahorro.
- Guardar estado o archivos en el disco de la instancia.
- Claves de acceso en el código o en el repositorio.
- Permisos de administrador para todo.
- Todo en una sola zona.
- Bases de datos y almacenamientos expuestos a Internet.
- No tener alertas de costo.
- Atarse a servicios propietarios sin evaluarlo, o evitarlos todos por miedo y perder su valor.

## Taller

1. Elige un proveedor y usa su capa gratuita. Activa las alertas de presupuesto antes de crear nada.
2. Despliega una aplicación Spring Boot sencilla en un servicio de contenedores gestionado, con una base de datos gestionada.
3. Configura el acceso de la aplicación a un almacenamiento de objetos mediante un rol, sin claves.
4. Dibuja la arquitectura con dos zonas de disponibilidad e identifica cada punto único de fallo que quede.
5. Estima el costo mensual con la calculadora del proveedor y propón dos formas de reducirlo.
6. Compara, para esa misma aplicación, IaaS, contenedores gestionados y *serverless*: esfuerzo operativo, costo y límites.

Rama `taller/n10-01-fundamentos`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Explica el modelo de responsabilidad compartida con un ejemplo de fallo de cada lado.
2. ¿Qué exige a una aplicación poder escalar horizontalmente?
3. ¿Cuándo *serverless* es buena idea y cuándo no?
4. ¿Cómo equilibras el uso de servicios gestionados con la dependencia del proveedor?
5. ¿Cómo controlas los costos en la nube?
6. ¿Qué es el mínimo privilegio y cómo lo aplicas a una aplicación?
