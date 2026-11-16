package contenedor.ciclo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Tiene un método @Async para que el contenedor le cree un proxy y se vea cuándo. */
@Component("beanObservado")
public class BeanObservado implements BeanNameAware, InitializingBean, DisposableBean {

    public BeanObservado(Dependencia dependencia) { Bitacora.registrar("1 constructor (con su dependencia)"); }

    @Autowired void setDependencia(Dependencia d) { Bitacora.registrar("2 inyección por setter"); }

    @Override public void setBeanName(String nombre) { Bitacora.registrar("3 BeanNameAware: " + nombre); }

    @PostConstruct void alIniciar() { Bitacora.registrar("5 @PostConstruct (¿es proxy? " + AopUtils.isAopProxy(this) + ")"); }

    @Override public void afterPropertiesSet() { Bitacora.registrar("6 afterPropertiesSet"); }

    @EventListener(ApplicationReadyEvent.class) public void listo() { Bitacora.registrar("8 ApplicationReadyEvent"); }

    @Async public void trabajoEnSegundoPlano() { }

    @PreDestroy void alCerrar() { Bitacora.registrar("9 @PreDestroy"); }

    @Override public void destroy() { Bitacora.registrar("10 DisposableBean.destroy"); }
}
