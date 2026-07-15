export type EstadoSucursal = 'ACTIVA' | 'INACTIVA';

export interface Branch {
  id: string;
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  estado: EstadoSucursal;
  horarioVisibleInicio: string | null;
  horarioVisibleFin: string | null;
}

export interface CreateBranchRequest {
  nombre: string;
  direccion: string;
  zonaHoraria: string;
  horarioVisibleInicio?: string;
  horarioVisibleFin?: string;
}

export interface UpdateBranchRequest {
  nombre?: string;
  direccion?: string;
  zonaHoraria?: string;
  horarioVisibleInicio?: string;
  horarioVisibleFin?: string;
}

export interface UpdateBranchStatusRequest {
  estado: EstadoSucursal;
}
