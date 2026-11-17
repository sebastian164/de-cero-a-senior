package tienda;


/** Demostraciones de los pasos 2, 4, 6, 7 y 8 con su resultado. */
public class Main {

    public static void main(String[] args) {
        System.out.println("2. Los cuatro caminos con TODOS los mensajes duplicados, sin idempotencia");
        System.out.println("   " + Escenarios.cuatroCaminos(Escenarios.duplicarTodo(), false).invariante(3));
        System.out.println("3. Lo mismo con idempotencia en participantes y orquestador");
        System.out.println("   [" + Escenarios.cuatroCaminos(Escenarios.duplicarTodo(), true).invariante(3) + "]");

        System.out.println("4. La liberación llega antes que la reserva");
        System.out.println("   sin lápidas: " + Escenarios.compensacionAntesQueLaAccion(false).invariante(1));
        System.out.println("   con lápidas: [" + Escenarios.compensacionAntesQueLaAccion(true).invariante(1) + "]");

        System.out.println("6. Compensación que falla siempre");
        Tienda t = new Tienda(5, new Broker());
        t.inventario.fallosAlLiberar = 100;
        t.comprar("s", 5_000_000);
        t.correr(60);
        t.almacen.sagas.get("s").transiciones.forEach(x -> System.out.println("   " + x));
        t.almacen.revisionHumana.forEach(x -> System.out.println("   revisión humana: " + x));

        System.out.println("7. Veinte compradores, una unidad, cien intercalados");
        for (Aislamiento.Estrategia e : Aislamiento.Estrategia.values()) {
            int dobles = 0;
            for (long semilla = 1; semilla <= 100; semilla++) {
                if (Aislamiento.vender(1, 20, e, semilla).dobleVenta(1)) {
                    dobles++;
                }
            }
            System.out.println("   " + e + ": doble venta en " + dobles + " de 100");
        }

        System.out.println("8. Informe de ventas a mitad de una saga que se cancela");
        Tienda i = new Tienda(5, new Broker());
        i.envios.sinCobertura.add("s");
        i.comprar("s", 100_000);
        i.correr(2);                             // ya cobró, todavía no sabe que el envío es imposible
        System.out.println("   informe sucio (cobros vigentes): " + i.pagos.cobros.size()
                + "; informe correcto (sagas completadas): " + Escenarios.ventasConfirmadas(i));
        i.correr(10);
        System.out.println("   al terminar: estado " + i.estado("s") + ", cobros vigentes " + i.pagos.cobros.size());
    }
}
