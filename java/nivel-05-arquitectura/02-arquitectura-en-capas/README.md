# 02 — Arquitectura en capas

## Idea sencilla

En un restaurante el cliente habla con el mesero, el mesero con la cocina y la cocina con la bodega. El cliente no entra a la bodega a buscar su tomate. Cada nivel atiende al de arriba y se apoya en el de abajo.

## Explicación técnica

El sistema se organiza en niveles horizontales. Cada capa tiene una responsabilidad y solo usa a la capa inmediatamente inferior.

```
┌──────────────────────────────┐
│ Presentación (controladores) │  HTTP, JSON, validación de entrada
├──────────────────────────────┤
│ Negocio (servicios)          │  reglas, casos de uso, transacciones
├──────────────────────────────┤
│ Persistencia (repositorios)  │  acceso a datos
├──────────────────────────────┤
│ Base de datos                │
└──────────────────────────────┘
        las dependencias apuntan hacia abajo
```

- **Capas estrictas:** una capa solo llama a la siguiente.
- **Capas relajadas:** una capa puede saltarse niveles (el controlador llama al repositorio). Más cómodo; debilita la separación.

Es la arquitectura por defecto de las aplicaciones Spring: `@RestController` → `@Service` → `@Repository`.

### Estructura habitual

```
com.tienda
├── controller    PedidoController, ClienteController
├── service       PedidoService, ClienteService
├── repository    PedidoRepository, ClienteRepository
├── model         Pedido, Cliente          (entidades JPA)
└── dto           PedidoRequest, PedidoResponse
```

### Por qué es tan popular

- Es fácil de explicar y de aprender.
- Encaja con cómo vienen organizados los frameworks.
- Para aplicaciones CRUD es suficiente y productiva.
- Cualquier desarrollador nuevo sabe dónde poner cada cosa.

## Sus problemas conocidos

**1. La base de datos queda en el centro.** Todas las dependencias terminan apuntando a la persistencia. El negocio depende del modelo de datos, las entidades JPA viajan hasta el controlador y un cambio de esquema se propaga hacia arriba. El diseño se vuelve "tablas primero".

**2. Modelo anémico.** Las entidades son bolsas de datos y toda la lógica acaba en servicios procedurales.

**3. Capas de paso.** Métodos que solo reenvían: el controlador llama a `service.buscar(id)`, que llama a `repository.findById(id)`, sin agregar nada. Es el antipatrón del sumidero (*sinkhole*).

**4. Baja cohesión funcional.** Para agregar un campo a "pedido" se tocan cinco paquetes. Lo que cambia junto está repartido por tipo técnico.

**5. El servicio dios.** `PedidoService` acumula todo lo relacionado con pedidos.

**6. Fugas entre capas.** Entidades con anotaciones de JSON, `LazyInitializationException` en el controlador, excepciones de SQL que llegan a la API.

```java
@GetMapping("/{id}")
public Pedido buscar(@PathVariable Long id) {     // devuelve la entidad JPA
    return pedidoRepository.findById(id).get();   // el controlador se salta el servicio
}
// La API quedó atada al esquema de la base: renombrar una columna rompe a los clientes.
```

## Capas bien hechas

- **DTOs en la frontera.** La entidad no sale del servicio; la API tiene su propio contrato.
- **Lógica en el dominio** cuando hay reglas: el servicio orquesta, la entidad decide.
- **Paquetes por funcionalidad, capas dentro:**

```
com.tienda
├── pedidos
│   ├── PedidoController
│   ├── PedidoService
│   ├── PedidoRepository
│   └── Pedido
└── clientes
    └── ...
```

- **Reglas verificadas con pruebas de arquitectura** (ArchUnit): "ningún controlador accede a un repositorio".
- **No crear capas de paso:** si una operación es una consulta simple, que el servicio no exista para ella es aceptable si el equipo lo acuerda.

## Trade-offs

| A favor | En contra |
|---|---|
| Simple y conocida | El negocio depende de la persistencia |
| Productiva para CRUD | Tiende al modelo anémico |
| Roles claros por capa | Cambios funcionales atraviesan todas las capas |
| Buen punto de partida | Difícil de mantener cuando las reglas crecen |

## Cuándo usarla y cuándo no

Úsala en aplicaciones CRUD, de reglas sencillas, con equipos pequeños o plazos cortos. Considera otra cuando las reglas de negocio son complejas, cuando la lógica debe sobrevivir a cambios de tecnología o cuando hay varios canales de entrada y varios sistemas externos.

## Taller

Construye (o toma) una aplicación en capas con dos entidades relacionadas y al menos una regla de negocio no trivial.

1. Dibuja las dependencias entre paquetes. ¿Hacia dónde apuntan todas?
2. Busca y lista: capas de paso, entidades expuestas en la API, lógica de negocio en controladores y lógica en consultas SQL.
3. Mueve una regla del servicio a la entidad. ¿Cómo cambió la prueba de esa regla?
4. Reorganiza los paquetes por funcionalidad y compara cuántos paquetes toca agregar un campo.
5. Escribe (o describe) una regla de ArchUnit que impida que un controlador use un repositorio.

Rama `taller/n5-02-capas`.

## Preguntas senior

1. ¿Por qué se dice que en la arquitectura en capas "la base de datos es el centro" y qué problema trae?
2. Capas estrictas o relajadas: ¿qué pierdes y qué ganas con cada una?
3. ¿Cuándo es legítimo que una capa sea solo de paso?
4. ¿Cómo evolucionas una aplicación en capas hacia una arquitectura centrada en el dominio sin reescribirla?
5. ¿Qué diferencia hay entre capa (*layer*) y nivel físico (*tier*)?

Siguiente: [03 — MVC](../03-mvc/).
