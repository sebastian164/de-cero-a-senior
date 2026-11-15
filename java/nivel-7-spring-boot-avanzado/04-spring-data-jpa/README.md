# 04 — Spring Data JPA a fondo

## Idea

JPA es un traductor entre objetos y tablas. Como todo traductor automático, acierta en lo simple y produce resultados costosos cuando no se le supervisa. La mayoría de los problemas de rendimiento de una aplicación Spring están aquí.

## Lo esencial

### Contexto de persistencia

- Hibernate mantiene en la transacción una caché de las entidades cargadas (primer nivel).
- **Estados:** nueva (*transient*), gestionada (*managed*), separada (*detached*), eliminada.
- **Detección de cambios (*dirty checking*):** al confirmar, Hibernate compara las entidades gestionadas con su estado original y genera los `UPDATE`. No hace falta llamar a `save` sobre una entidad ya gestionada.

### El problema N+1

Cargar N pedidos y luego acceder a `pedido.getCliente()` en un bucle dispara una consulta por cada pedido: 1 + N consultas.

| Solución | Cómo |
|---|---|
| `JOIN FETCH` | `select p from Pedido p join fetch p.cliente` |
| `@EntityGraph` | Declarar qué asociaciones cargar en un método del repositorio |
| Proyecciones | Consultar un DTO con solo las columnas necesarias |
| Carga por lotes | `hibernate.default_batch_fetch_size` |

### Carga perezosa y ansiosa

- `@ManyToOne` y `@OneToOne` son `EAGER` por defecto: cámbialas a `LAZY`.
- `@OneToMany` y `@ManyToMany` son `LAZY` por defecto.
- `LazyInitializationException`: acceso a una asociación perezosa fuera de la sesión.
- **Open Session in View** (`spring.jpa.open-in-view`) está activado por defecto y lo oculta manteniendo la sesión abierta durante la vista. Desactívalo: esconde consultas en la capa web.

### Formas de consultar

| Mecanismo | Cuándo |
|---|---|
| Consultas derivadas (`findByEstadoAndCliente`) | Consultas simples |
| `@Query` con JPQL | Consultas específicas |
| Proyecciones (interfaces o `record`) | Lecturas: solo lo necesario |
| `Specification` / Criteria | Filtros dinámicos |
| SQL nativo, jOOQ, `JdbcClient` | Reportes y consultas complejas |

## Ejemplo

```java
interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("select p from Pedido p join fetch p.cliente where p.estado = :estado")
    List<Pedido> conClientePorEstado(EstadoPedido estado);

    @EntityGraph(attributePaths = {"lineas", "cliente"})
    Optional<Pedido> findConDetalleById(Long id);

    // Proyección: una consulta, sin entidades gestionadas
    @Query("""
            select new com.tienda.pedidos.PedidoResumen(p.id, c.nombre, p.total)
            from Pedido p join p.cliente c
            where p.fecha >= :desde
            """)
    Page<PedidoResumen> resumenDesde(LocalDate desde, Pageable pagina);
}
```

## Errores comunes

- N+1 sin detectar por no mirar el SQL generado.
- `findAll()` sobre tablas grandes, sin paginar.
- Cargar entidades completas para mostrar tres columnas.
- `CascadeType.ALL` y `orphanRemoval` aplicados sin pensar.
- `@ManyToMany` con `List` (genera borrados e inserciones masivos): usar `Set` o una entidad intermedia.
- `JOIN FETCH` de dos colecciones a la vez (producto cartesiano) o con paginación (se pagina en memoria).
- `equals`/`hashCode` generados por `@Data`.
- Exponer entidades en la API.

## Taller

1. Activa el registro de SQL y reproduce un N+1 con 50 pedidos. Cuenta las consultas. Corrígelo de tres formas y compara.
2. Desactiva `open-in-view` y arregla lo que se rompa.
3. Reemplaza una consulta que carga entidades por una proyección a `record` y compara el SQL.
4. Implementa un filtro dinámico con cuatro criterios opcionales usando `Specification`.
5. Provoca el aviso de paginación en memoria con `JOIN FETCH` y resuélvelo.
6. Demuestra la detección de cambios: modifica una entidad gestionada sin llamar a `save`.

Rama `taller/n7-04-jpa`.

## Preguntas senior

1. ¿Qué es el problema N+1 y cómo lo detectas en un sistema en producción?
2. ¿Por qué desactivar Open Session in View?
3. ¿Cuándo usar entidades y cuándo proyecciones?
4. ¿Qué es el contexto de persistencia y cómo afecta a transacciones largas?
5. ¿Cuándo abandonas JPA por SQL directo?
6. `EAGER` frente a `LAZY`: ¿por qué `LAZY` por defecto?
