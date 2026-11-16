package oficina.reservas.adaptadores.persistencia.jpa;

import oficina.reservas.dominio.Intervalo;
import oficina.reservas.dominio.Reserva;
import oficina.reservas.dominio.ReservaId;
import oficina.reservas.dominio.SalaId;

final class ReservaMapper {
    private ReservaMapper() { }

    static ReservaJpaEntity aEntidad(Reserva r) {
        return new ReservaJpaEntity(r.id().valor(), r.sala().valor(), r.usuario(),
                r.intervalo().inicio(), r.intervalo().fin(), r.estado().name());
    }

    static Reserva aDominio(ReservaJpaEntity e) {
        return new Reserva(new ReservaId(e.id), new SalaId(e.sala), e.usuario,
                new Intervalo(e.inicio, e.fin), Reserva.Estado.valueOf(e.estado));
    }
}
