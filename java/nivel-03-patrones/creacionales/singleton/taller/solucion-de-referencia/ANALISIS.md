# Singleton — análisis

## 1. El perezoso del "antes" crea varias instancias
`Carrera.contarConstrucciones` arranca 32 hilos detenidos en un `CountDownLatch` y los suelta a la vez contra `getInstancia()`. El constructor tarda 50 ms (simula leer un archivo), así que muchos hilos ven `instancia == null` antes de que el primero termine. En la ejecución real del `Main` salieron decenas de construcciones (la cifra exacta varía; la prueba exige `> 1`).

## 2. Tres correcciones
| Forma | Perezosa | Costo por llamada | Notas |
|---|---|---|---|
| `synchronized` en `getInstancia` | sí | toma el candado siempre | la más fácil de entender |
| *Holder idiom* | sí (al primer acceso a `Holder`) | ninguno | la JVM inicializa una clase una sola vez y de forma segura entre hilos |
| `enum` | al cargar el enum | ninguno | además resiste serialización y reflexión (`Constructor.newInstance` lanza excepción) |

Las tres dan exactamente 1 construcción con los mismos 32 hilos (`lasTresCorreccionesCreanUnaSola`).

## 3. Estado mutable que contamina pruebas
`RegistroGlobal` es un singleton `enum` con una lista. La "prueba B" espera 1 alerta tras la suya: pasa si corre sola y falla con 2 si antes corrió la "prueba A". La prueba `conEstadoGlobalElResultadoDependeDelOrden` reproduce ambos casos.

## 4. Inyección
`ServicioAlertas` recibe su `Registro` en el constructor. Cada prueba crea el suyo, no hay que limpiar nada entre pruebas, se puede pasar un doble, y la dependencia se ve en la firma. Sigue pudiendo existir una sola instancia: la decide quien ensambla.

## 5. `@Service` con `List<String> errores`
Un bean singleton atiende todas las peticiones con la misma instancia. Con dos peticiones simultáneas, ambas agregan a la misma lista: la petición 1 devuelve los errores de la 2, y como `ArrayList` no es segura entre hilos se pueden perder elementos o lanzar `ConcurrentModificationException`/`ArrayIndexOutOfBoundsException`.

Se comprobó con un proyecto Spring de pruebas (`SingletonConEstadoTest`): dos peticiones concurrentes al mismo bean y cada una ve los errores de la otra.

Tres correcciones:
1. Variable local: la lista se crea dentro del método y se devuelve (la mejor; el bean queda sin estado). También verificado en la misma prueba.
2. Alcance `@RequestScope` (o `prototype` obtenido con `ObjectProvider`) para el objeto que acumula.
3. `ThreadLocal` limpiado en `finally` (último recurso; se olvida limpiar con hilos reutilizados).
