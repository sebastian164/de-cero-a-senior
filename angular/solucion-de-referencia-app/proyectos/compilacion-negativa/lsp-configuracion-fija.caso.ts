// ESPERA: TS2339
// README n2-03: "Pedir escritura a ConfiguracionFija ya no compila."
import { ConfiguracionFija } from '../ejemplos/nivel-02-solid/03-lsp/almacenamiento';
new ConfiguracionFija().guardar('tema', 'oscuro');
