class ArchivoLog {
    private final String ruta;
    ArchivoLog(String ruta) { this.ruta = ruta; }
    void escribirLinea(String linea) { System.out.println("[" + ruta + "] " + linea); }
}
