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

export interface LeaveRequestFilters {
  employeeId?: string;
  estado?: EstadoLicencia;
}
