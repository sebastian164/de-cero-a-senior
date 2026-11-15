package banco.jpa;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.EventoDeCuenta;
import banco.aplicacion.puertos.salida.OrdenesProcesadas;
import banco.aplicacion.puertos.salida.PublicadorDeEventos;
import banco.aplicacion.puertos.salida.RegistroDeMovimientos;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Movimiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Los puertos de salida del banco, sobre JPA. Es el único sitio del sistema que sabe que hay un ORM. */
@Repository
public class AlmacenJpa implements RepositorioDeCuentas, RegistroDeMovimientos, OrdenesProcesadas, PublicadorDeEventos {

    @PersistenceContext                                          // el EntityManager de la transacción en curso
    private EntityManager entidades;

    // ---- RepositorioDeCuentas ----

    @Override
    public String siguienteId() {
        return "cuenta-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        CuentaJpa fila = entidades.find(CuentaJpa.class, cuenta.id());
        if (fila == null) {
            entidades.persist(CuentaJpa.nueva(cuenta));                  // INSERT al confirmar
            return;
        }
        if (fila.version() != cuenta.version()) {
            throw new ConflictoDeConcurrencia("la cuenta " + cuenta.id());   // se leyó en otra transacción y ya cambió
        }
        fila.copiarDesde(cuenta);     // sin "guardar": al confirmar, Hibernate ve el cambio y emite
                                      // UPDATE cuenta SET … , version = ? WHERE id = ? AND version = ?
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(entidades.find(CuentaJpa.class, id)).map(CuentaJpa::aDominio);
    }

    // ---- RegistroDeMovimientos ----

    @Override
    public void registrar(Movimiento movimiento) {
        entidades.persist(MovimientoJpa.desde(movimiento));
    }

    @Override
    public List<Movimiento> de(String cuentaId) {
        return entidades.createQuery("select m from MovimientoJpa m where m.cuentaId = :cuenta order by m.id", MovimientoJpa.class)
                .setParameter("cuenta", cuentaId)
                .getResultList().stream().map(MovimientoJpa::aDominio).toList();
    }

    // ---- OrdenesProcesadas ----

    @Override
    public boolean registrarSiEsNueva(String clave) {
        // SQL nativo a propósito. Una sola sentencia atómica: si otra transacción está insertando la misma
        // clave, esta espera a que termine; si aquella confirma, aquí no se inserta nada y la orden es repetida.
        int insertadas = entidades.createNativeQuery("insert into orden_procesada (clave) values (:clave) on conflict do nothing")
                .setParameter("clave", clave)
                .executeUpdate();
        return insertadas == 1;
    }

    // ---- PublicadorDeEventos: la bandeja de salida ----

    @Override
    public void publicar(EventoDeCuenta evento) {
        entidades.persist(EventoPendienteJpa.desde(evento));
    }
}
