public class Main {

    public static void main(String[] args) {
        procesarVenta(new FabricaColombia(), 100_000);
        procesarVenta(new FabricaMexico(), 100_000);
    }

    /** El cliente trabaja con la familia completa sin saber cuál es. */
    static void procesarVenta(FabricaPais fabrica, long valor) {
        Cobrador cobrador = fabrica.crearCobrador();
        Facturador facturador = fabrica.crearFacturador();
        System.out.println(cobrador.cobrar(valor) + " | " + facturador.facturar(valor));
    }
}

interface Cobrador { String cobrar(long valor); }
interface Facturador { String facturar(long valor); }

/** La fábrica abstracta: un método de creación por cada miembro de la familia. */
interface FabricaPais {
    Cobrador crearCobrador();
    Facturador crearFacturador();
}

class FabricaColombia implements FabricaPais {
    @Override public Cobrador crearCobrador() { return valor -> "PSE cobra $" + valor + " COP"; }
    @Override public Facturador crearFacturador() { return valor -> "Factura DIAN con IVA 19%"; }
}

class FabricaMexico implements FabricaPais {
    @Override public Cobrador crearCobrador() { return valor -> "SPEI cobra $" + valor + " MXN"; }
    @Override public Facturador crearFacturador() { return valor -> "CFDI SAT con IVA 16%"; }
}
