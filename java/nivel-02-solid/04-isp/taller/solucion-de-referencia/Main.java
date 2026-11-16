public class Main {
    public static void main(String[] args) {
        ServicioUsuariosEnMemoria servicio = new ServicioUsuariosEnMemoria();
        RegistroDeUsuarios registro = servicio;
        PerfilDeUsuario perfil = servicio;
        AdministracionDeUsuarios administracion = servicio;
        registro.registrar("ana", "ana@correo.example");
        System.out.println(perfil.buscar("ana"));
        System.out.println(administracion.listarTodos());
        System.out.println(new TableroMetricas(servicio).resumen());
    }
}
