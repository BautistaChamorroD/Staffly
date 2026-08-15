export type EstadoAdelanto = 'PENDIENTE' | 'DESCONTADO' | 'CANCELADO';

export interface Advance {
  id: string;
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  fecha: string;
  monto: number;
  motivo: string | null;
  estado: EstadoAdelanto;
}

export interface CreateAdvanceRequest {
  employeeId: string;
  fecha: string;
  monto: number;
  motivo?: string;
}
