// ESPERA: TS2741
// README n3-02: "El tipo Record<Modalidad, …> obliga a cubrir todas las modalidades: si se agrega una a la unión, no compila hasta definirla."
type Modalidad = 'estandar' | 'express' | 'recogida' | 'dron';
type CalculoEnvio = (pesoKg: number) => number;
export const CALCULOS: Record<Modalidad, CalculoEnvio> = {
  estandar: (kg) => 8000 + kg * 500,
  express: (kg) => 15000 + kg * 900,
  recogida: () => 0,
};
