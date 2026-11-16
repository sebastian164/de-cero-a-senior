package cache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CatalogoProductos {

    private final RepositorioProductos repositorio;
    private final CacheManager caches;
    private final Map<Long, ReentrantLock> bloqueos = new ConcurrentHashMap<>();

    public CatalogoProductos(RepositorioProductos repositorio, CacheManager caches) {
        this.repositorio = repositorio;
        this.caches = caches;
    }

    @Cacheable(cacheNames = "productos", key = "#id")
    public ProductoDto buscar(Long id) {
        return repositorio.buscarDto(id);
    }

    /** Actualiza sin invalidar: deja la copia de la caché desactualizada. */
    public void actualizarPrecioSinInvalidar(Long id, BigDecimal precio) {
        repositorio.actualizarPrecio(id, precio);
    }

    @CacheEvict(cacheNames = "productos", key = "#id")
    public void actualizarPrecio(Long id, BigDecimal precio) {
        repositorio.actualizarPrecio(id, precio);
    }

    @Cacheable(cacheNames = "productos-ttl-corto", key = "#id")
    public ProductoDto buscarConTtlCorto(Long id) {
        return repositorio.buscarDto(id);
    }

    /** Sin protección: al expirar la clave, todas las peticiones simultáneas van a la base. */
    @Cacheable(cacheNames = "productos-estampida", key = "#id")
    public ProductoDto buscarSinProteccion(Long id) {
        return repositorio.buscarDto(id);
    }

    /**
     * sync = true: en la documentación de Spring, "una sola petición por clave carga el valor". Con la caché de
     * Redis por defecto (RedisCacheWriter sin bloqueo) no protege nada: la prueba lo muestra.
     */
    @Cacheable(cacheNames = "productos-estampida", key = "#id", sync = true)
    public ProductoDto buscarSincronizado(Long id) {
        return repositorio.buscarDto(id);
    }

    /** Mitigación: bloqueo local por clave y doble comprobación. Una consulta por instancia de la aplicación. */
    public ProductoDto buscarConBloqueoPorClave(Long id) {
        Cache cache = caches.getCache("productos-estampida");
        ProductoDto enCache = cache.get(id, ProductoDto.class);
        if (enCache != null) {
            return enCache;
        }
        ReentrantLock bloqueo = bloqueos.computeIfAbsent(id, k -> new ReentrantLock());
        bloqueo.lock();
        try {
            enCache = cache.get(id, ProductoDto.class);       // otro hilo pudo cargarla mientras esperábamos
            if (enCache != null) {
                return enCache;
            }
            ProductoDto dto = repositorio.buscarDto(id);
            cache.put(id, dto);
            return dto;
        } finally {
            bloqueo.unlock();
        }
    }

    /** Autoinvocación: this.buscar no pasa por el proxy, así que no consulta la caché. */
    public List<ProductoDto> buscarVarios(List<Long> ids) {
        return ids.stream().map(this::buscar).toList();
    }
}
