export type EstadoRecibo = 'GENERADO' | 'PAGADO' | 'ANULADO' | 'AJUSTE';
export type TipoRecibo = 'NORMAL' | 'AJUSTE';

export interface DeduccionLinea {
  nombre: string;
  tipo: string;
  monto: number;
}

export interface Payslip {
  id: string;
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  payrollPeriodId: string;
  periodoInicio: string;
  periodoFin: string;
  estado: EstadoRecibo;
  tipo: TipoRecibo;
  payslipOriginalId: string | null;
  sueldoBase: number;
  horasNormales: number;
  horasExtra: number;
  horasFeriado: number;
  montoHorasExtra?: number;
  montoHorasFeriado?: number;
  brutoCalculado: number;
  detalleDescuentos: DeduccionLinea[];
  totalDeducciones: number;
  totalAdelantos: number;
  netoFinal: number;
  fechaPago: string | null;
}

export interface VoidPayslipRequest {
  motivo?: string;
}
