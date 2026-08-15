export interface HoursWorkedRow {
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  branchId: string;
  branchNombre: string;
  horasNormales: number;
  horasExtra: number;
  horasFeriado: number;
  totalHoras: number;
}

export interface PayrollCostRow {
  branchId: string;
  branchNombre: string;
  cantidadEmpleados: number;
  totalBruto: number;
  totalDeducciones: number;
  totalNeto: number;
}

export interface PendingAdvanceRow {
  id: string;
  employeeId: string;
  employeeNombre: string;
  employeeApellido: string;
  monto: number;
  fecha: string;
  motivo: string | null;
}

export type ReportType = 'hours-worked' | 'payroll-cost' | 'pending-advances';
export type ExportFormat = 'pdf' | 'csv';
