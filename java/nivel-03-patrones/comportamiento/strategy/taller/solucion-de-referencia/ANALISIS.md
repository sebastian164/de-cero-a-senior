# Strategy — análisis

Ejemplo: 6 horas extra diurnas y 4 nocturnas, valor hora $20.000.

1. **Condicionales** (`LiquidadorCondicional`): `if` por país. CO = 10 × 20.000 × 1,25 = $250.000; MX = 6 × 25.000 + 4 × 35.000 = $290.000; ES = 10 horas libres.

2. **Strategy**: `PoliticaHorasExtra.liquidar(horas, valorHora) -> Liquidacion`. Como la tercera política no devuelve dinero, `Liquidacion` es un tipo **sellado** con dos casos, `Pago(pesos)` y `Compensacion(horasLibres)`. Se descartaron: devolver `long` con 0 para la compensación (pierde la información de las horas), un `record` con ambos campos y uno en cero (estados inválidos representables) y una excepción. Con `sealed`, el `switch` del consumidor (nómina frente a calendario) es exhaustivo y el compilador avisa si aparece un tercer tipo de resultado.

3. **Cuarta política** (`RecargoConTope`: 50 % hasta 10 horas, el resto sin recargo): una clase nueva y una entrada en el registro. `Liquidador` no cambió; la prueba `agregarUnaPoliticaNoModificaElLiquidador` agrega una quinta desde afuera.

4. **¿Lambda o clase?** Las tres primeras son lambdas: una fórmula pura, sin estado ni parámetros, que cabe en una línea. `RecargoConTope` no debería ser lambda: tiene configuración (porcentaje y tope) que se valida al construirse, un nombre de negocio y una lógica de varias líneas que merece pruebas propias. Regla: lambda si es una expresión sin estado; clase si tiene configuración, validación, dependencias o más de una responsabilidad.

5. **¿Dónde quedó la decisión?** En `Politicas.REGISTRO`, el mapa país → política, que es configuración y se construye una vez; el `Liquidador` solo busca en el mapa que recibe. El responsable es quien ensambla la aplicación (en Spring, una clase `@Configuration` o una propiedad por filial que elige el bean). El negocio que liquida no decide ni conoce los países.
