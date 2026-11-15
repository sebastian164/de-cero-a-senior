public class Main {

    public static void main(String[] args) {
        System.out.println(costoEnvio("MOTO", 3, 8));
        System.out.println(costoEnvio("CAMION", 300, 120));
        System.out.println(tiempoEstimadoHoras("MOTO", 8));
        System.out.println(puedeLlevar("MOTO", 40));
    }

    static long costoEnvio(String transporte, int pesoKg, int distanciaKm) {
        switch (transporte) {
            case "MOTO": return 3_000 + distanciaKm * 500L;
            case "CAMION": return 50_000 + distanciaKm * 1_200L + pesoKg * 100L;
            case "BICICLETA": return 2_000 + distanciaKm * 300L;
            default: return 0;
        }
    }

    static int tiempoEstimadoHoras(String transporte, int distanciaKm) {
        switch (transporte) {
            case "MOTO": return Math.max(1, distanciaKm / 30);
            case "CAMION": return Math.max(2, distanciaKm / 50);
            case "BICICLETA": return Math.max(1, distanciaKm / 12);
            default: return 0;
        }
    }

    static boolean puedeLlevar(String transporte, int pesoKg) {
        switch (transporte) {
            case "MOTO": return pesoKg <= 20;
            case "CAMION": return pesoKg <= 5_000;
            default: return false; // la bicicleta quedó sin caso
        }
    }
}
