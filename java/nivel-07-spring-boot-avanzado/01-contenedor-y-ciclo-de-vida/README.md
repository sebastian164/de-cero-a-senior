# 01 — El contenedor IoC y el ciclo de vida de los beans

## Idea

Spring es un ensamblador: tú declaras las piezas y qué necesita cada una, y él las crea en el orden correcto y las conecta. Entender cómo lo hace convierte los errores "mágicos" en errores explicables.

## Lo esencial

- **Bean:** un objeto creado y gestionado por el contenedor (`ApplicationContext`).
- **Cómo se declaran:** anotaciones de estereotipo (`@Component`, `@Service`, `@Repository`, `@Controller`) detectadas por escaneo, o métodos `@Bean` en una clase `@Configuration`.
- **Inyección:** por constructor (preferida: dependencias `final`, visibles y probables), por setter (opcionales) o por campo (evitar).
- **Resolución cuando hay varias implementaciones:** `@Primary`, `@Qualifier`, o inyectar `List<T>` / `Map<String, T>`.

### Alcances

| Alcance | Instancias |
|---|---|
| `singleton` (por defecto) | Una por contenedor |
| `prototype` | Una nueva cada vez que se pide |
| `request`, `session` | Una por petición o sesión web |

Inyectar un bean de alcance corto en un singleton exige `ObjectProvider<T>` o un proxy de alcance; de lo contrario se inyecta una única vez.

### Ciclo de vida

1. Se leen las definiciones de beans.
2. Se instancia (constructor) y se inyectan dependencias.
3. Se ejecutan los `BeanPostProcessor` en su fase "antes de inicializar" y luego `@PostConstruct`.
4. Se ejecutan los `BeanPostProcessor` en su fase "después de inicializar": aquí se crean los proxies de AOP. Dentro de `@PostConstruct` todavía se está en el objeto real, sin proxy.
5. El bean está en uso.
6. `@PreDestroy` al cerrar el contexto.

Eventos útiles: `ApplicationReadyEvent` para ejecutar algo cuando la aplicación ya arrancó.

## Ejemplo

```java
@Configuration
class PagosConfig {

    @Bean
    @ConditionalOnProperty(name = "pagos.proveedor", havingValue = "stripe")
    PasarelaPagos pasarelaStripe(PagosProperties propiedades) {
        return new PasarelaStripe(propiedades.claveApi());
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}

@Service
class ConfirmarPedido {
    private final RepositorioPedidos repositorio;
    private final PasarelaPagos pasarela;

    ConfirmarPedido(RepositorioPedidos repositorio, PasarelaPagos pasarela) {   // sin @Autowired
        this.repositorio = repositorio;
        this.pasarela = pasarela;
    }
}
```

## Errores comunes

- Inyección por campo: oculta dependencias y dificulta las pruebas.
- Dependencias circulares resueltas con `@Lazy` en lugar de rediseñar.
- Lógica pesada o llamadas remotas en constructores o `@PostConstruct`: alargan y fragilizan el arranque.
- Estado mutable en singletons.
- Crear con `new` una clase que debería ser bean y esperar que funcionen `@Transactional` o `@Value`.
- Escaneo de paquetes demasiado amplio.

## Taller

1. Crea una interfaz con tres implementaciones e inyéctalas de cuatro formas: `@Primary`, `@Qualifier`, `List` y `Map`.
2. Registra en consola cada fase del ciclo de vida de un bean.
3. Provoca una dependencia circular, lee el error y corrígela rediseñando.
4. Inyecta un bean `prototype` en un singleton y demuestra el problema; corrígelo con `ObjectProvider`.
5. Declara un bean condicional por propiedad y cambia la implementación sin recompilar.

Rama `taller/n7-01-contenedor`.

## Preguntas senior

1. ¿Por qué se prefiere la inyección por constructor?
2. Diferencia entre `@Component` y `@Bean`.
3. ¿En qué momento del ciclo de vida se crean los proxies y qué implica?
4. ¿Cómo resuelve Spring la ambigüedad entre varias implementaciones?
5. ¿Qué problema hay al inyectar un bean de alcance `request` en un singleton?
