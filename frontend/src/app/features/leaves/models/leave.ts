export type EstadoLicencia = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';

export interface LeaveType {
  id: string;
  nombre: string;
  esPaga: boolean;
  cupoAnual: number | null;
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  leaveTypeId: string;
  leaveTypeNombre: string;
  fechaInicio: string;
  fechaFin: string;
  motivo: string | null;
  estado: EstadoLicencia;
  aprobadoPorId: string | null;
  tieneConflicto: boolean;
}

export interface CreateLeaveRequestBody {
  leaveTypeId: string;
  fechaInicio: string;
  fechaFin: string;
  motivo?: string;
  employeeId?: string;
}

export interface RejectLeaveRequestBody {
  motivo: string;
}
