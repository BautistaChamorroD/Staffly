export type TipoTurno = 'FIJO' | 'ROTATIVO';
export type EstadoTurno = 'PLANIFICADO' | 'CONFIRMADO' | 'CUMPLIDO' | 'AUSENTE';

export interface Schedule {
  id: string;
  employeeId: string;
  branchId: string;
  fechaHoraInicio: string; // "2026-08-10T08:00:00"
  fechaHoraFin: string;
  tipoTurno: TipoTurno;
  estado: EstadoTurno;
  horasTotales: number;
  warning: string | null;
}

export interface CreateScheduleRequest {
  employeeId: string;
  branchId: string;
  fechaHoraInicio: string;
  fechaHoraFin: string;
  tipoTurno: TipoTurno;
}

export interface UpdateScheduleRequest {
  branchId?: string;
  fechaHoraInicio?: string;
  fechaHoraFin?: string;
  tipoTurno?: TipoTurno;
}

export interface UpdateStatusRequest {
  estado: 'CUMPLIDO' | 'AUSENTE';
}

export interface DuplicateWeeklyRequest {
  mesObjetivo: number;
  anioObjetivo: number;
}

export interface DuplicateWeeklyResponse {
  turnosCreados: Schedule[];
  advertencia: string | null;
}

export interface ScheduleFilters {
  employeeId?: string;
  branchId?: string;
  desde?: string;
  hasta?: string;
}
