export type EstadoLicencia = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';

export interface LeaveType {
  id: string;
  nombre: string;
  esPaga: boolean;
  tieneCupoAnual: boolean;
  cuposDiasAnual: number | null;
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  leaveTypeId: string;
  leaveTypeName: string;
  fechaInicio: string;
  fechaFin: string;
  motivo: string | null;
  estado: EstadoLicencia;
  aprobadoPorId: string | null;
}

export interface CreateLeaveRequestBody {
  leaveTypeId: string;
  fechaInicio: string;
  fechaFin: string;
  motivo?: string;
}

export interface RejectLeaveRequestBody {
  motivo: string;
}
