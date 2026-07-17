export type EstadoLaboral = 'ACTIVO' | 'LICENCIA' | 'SUSPENDIDO' | 'BAJA';
export type EstadoLiquidacion = 'AL_DIA' | 'PENDIENTE';
export type TipoContrato = 'JORNADA_COMPLETA' | 'JORNADA_PARCIAL' | 'POR_HORA';

export interface Employee {
  id: string;
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso: string | null;
  tipoContrato: TipoContrato;
  categoria: string;
  sueldoBase: number;
  telefono: string | null;
  emailContacto: string | null;
  estadoLaboral: EstadoLaboral;
  estadoLiquidacion: EstadoLiquidacion;
  branchIds: string[];
}

export interface CreateEmployeeRequest {
  nombre: string;
  apellido: string;
  documento: string;
  fechaNacimiento: string;
  fechaIngreso: string;
  fechaEgreso?: string;
  tipoContrato: TipoContrato;
  categoria: string;
  sueldoBase: number;
  telefono?: string;
  emailContacto?: string;
  branchIds: string[];
}

export interface UpdateEmployeeRequest {
  nombre?: string;
  apellido?: string;
  documento?: string;
  fechaNacimiento?: string;
  fechaIngreso?: string;
  fechaEgreso?: string;
  tipoContrato?: TipoContrato;
  categoria?: string;
  sueldoBase?: number;
  telefono?: string;
  emailContacto?: string;
  branchIds?: string[];
}

export interface UpdateEmployeeStatusRequest {
  estadoLaboral: EstadoLaboral;
}

export interface EmployeeListFilters {
  estadoLaboral?: EstadoLaboral;
  branchId?: string;
  search?: string;
}
