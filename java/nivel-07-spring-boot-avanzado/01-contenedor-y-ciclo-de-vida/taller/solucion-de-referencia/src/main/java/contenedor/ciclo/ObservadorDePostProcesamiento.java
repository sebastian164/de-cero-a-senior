package contenedor.ciclo;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/** Sin Ordered: Spring lo ejecuta después de los post-procesadores ordenados, así ve el objeto tal como queda. */
@Component
class ObservadorDePostProcesamiento implements BeanPostProcessor {

    @Override public Object postProcessBeforeInitialization(Object bean, String nombre) {
        if (nombre.equals("beanObservado")) Bitacora.registrar("4 BeanPostProcessor antes de inicializar (¿es proxy? " + AopUtils.isAopProxy(bean) + ")");
        return bean;
    }

    @Override public Object postProcessAfterInitialization(Object bean, String nombre) {
        if (nombre.equals("beanObservado")) Bitacora.registrar("7 BeanPostProcessor después de inicializar (¿es proxy? " + AopUtils.isAopProxy(bean) + ")");
        return bean;
    }
}
