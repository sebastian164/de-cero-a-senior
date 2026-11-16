package transacciones.auditoria;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Aspect
@Component
public class AspectoDeAuditoria {
    private static final Logger log = LoggerFactory.getLogger(AspectoDeAuditoria.class);
    public final List<String> registros = new CopyOnWriteArrayList<>();

    @Around("@annotation(auditable)")
    Object auditar(ProceedingJoinPoint punto, Auditable auditable) throws Throwable {
        String quien = UsuarioActual.quien();
        String que = auditable.value().isBlank() ? punto.getSignature().toShortString() : auditable.value();
        try {
            Object resultado = punto.proceed();
            registros.add(quien + " ejecutó " + que + ": OK");
            return resultado;
        } catch (Throwable e) {
            registros.add(quien + " ejecutó " + que + ": FALLÓ (" + e.getMessage() + ")");
            throw e;
        } finally {
            log.info("auditoría: {}", registros.getLast());
        }
    }
}
