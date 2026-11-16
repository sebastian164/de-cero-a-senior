package colecciones;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Con Hibernate real (H2): el producto sigue encontrándose en el Set después de recibir su id. */
@SpringBootTest
class ProductoJpaTest {

    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;

    @Test
    void sigueEnElConjuntoDespuesDePersistirYEsIgualAlCargado() {
        Producto nuevo = new Producto("teclado");
        Set<Producto> conjunto = new HashSet<>();
        conjunto.add(nuevo);
        tx.executeWithoutResult(s -> em.persist(nuevo));
        assertThat(nuevo.getId()).isNotNull();
        assertThat(conjunto.contains(nuevo)).isTrue();

        Producto cargado = tx.execute(s -> em.find(Producto.class, nuevo.getId()));
        assertThat(cargado).isNotSameAs(nuevo).isEqualTo(nuevo);
        assertThat(conjunto).contains(cargado);
        assertThat(new Producto("a")).isNotEqualTo(new Producto("a"));     // dos nuevos sin id no son iguales
    }

    @Test
    void conHashCodePorIdElObjetoSePierdeAlPersistir() {
        // Contraejemplo: si hashCode dependiera del id, cambiaría al persistir
        record ConHashPorId(Long[] id) {
            @Override public int hashCode() { return java.util.Objects.hashCode(id[0]); }
            @Override public boolean equals(Object o) { return o instanceof ConHashPorId c && java.util.Objects.equals(id[0], c.id[0]); }
        }
        Long[] id = {null};
        var entidad = new ConHashPorId(id);
        Set<ConHashPorId> conjunto = new HashSet<>(Set.of(entidad));
        id[0] = 42L;                                      // "persistir" asigna el id
        assertThat(conjunto.contains(entidad)).isFalse();
    }
}
