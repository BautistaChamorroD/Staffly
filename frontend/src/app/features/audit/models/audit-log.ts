export interface AuditLogEntry {
  id: string;
  entidad: string;
  entidadId: string;
  userId: string;
  userName: string;
  accion: string;
  descripcion: string;
  timestamp: string;
  cambios: Record<string, unknown> | null;
}

export interface AuditLogFilters {
  entidad?: string;
  entidadId?: string;
  userId?: string;
  desde?: string;
  hasta?: string;
}
