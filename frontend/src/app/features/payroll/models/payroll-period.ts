export type EstadoPeriodo = 'ABIERTO' | 'CERRADO' | 'REABIERTO';

export interface PayrollPeriod {
  id: string;
  fechaInicio: string;
  fechaFin: string;
  estado: EstadoPeriodo;
  fechaCierre: string | null;
}

export interface CreatePayrollPeriodRequest {
  fechaInicio: string;
  fechaFin: string;
}
