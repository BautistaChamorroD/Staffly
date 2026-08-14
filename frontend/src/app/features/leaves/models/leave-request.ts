export type EstadoLicencia = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';

export interface LeaveRequest {
  id: string;
  employeeId: string;
  leaveTypeId: string;
  leaveTypeNombre: string;
  fechaInicio: string; // "YYYY-MM-DD"
  fechaFin: string;
  motivo: string | null;
  estado: EstadoLicencia;
  motivoRechazo: string | null;
}

export interface CreateLeaveRequestRequest {
  employeeId?: string; // only for ADMIN / RRHH on behalf of employee
  leaveTypeId: string;
  fechaInicio: string;
  fechaFin: string;
  motivo?: string;
}

export interface RejectLeaveRequestRequest {
  motivo: string;
}

export interface LeaveRequestFilters {
  employeeId?: string;
  estado?: EstadoLicencia;
}
