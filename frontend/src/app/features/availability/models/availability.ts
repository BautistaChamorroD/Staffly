export type DiaSemana =
  | 'LUNES'
  | 'MARTES'
  | 'MIERCOLES'
  | 'JUEVES'
  | 'VIERNES'
  | 'SABADO'
  | 'DOMINGO';

export const DIA_SEMANA_LABELS: Record<DiaSemana, string> = {
  LUNES: 'Lunes',
  MARTES: 'Martes',
  MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves',
  VIERNES: 'Viernes',
  SABADO: 'Sábado',
  DOMINGO: 'Domingo',
};

export const DIA_SEMANA_ORDER: DiaSemana[] = [
  'LUNES',
  'MARTES',
  'MIERCOLES',
  'JUEVES',
  'VIERNES',
  'SABADO',
  'DOMINGO',
];

export interface Availability {
  id: string;
  diaSemana: DiaSemana;
  horaInicio: string; // "HH:mm:ss"
  horaFin: string;    // "HH:mm:ss"
}

export interface CreateAvailabilityRequest {
  diaSemana: DiaSemana;
  horaInicio: string; // "HH:mm"
  horaFin: string;    // "HH:mm"
}

export interface UpdateAvailabilityRequest {
  diaSemana?: DiaSemana;
  horaInicio?: string;
  horaFin?: string;
}
