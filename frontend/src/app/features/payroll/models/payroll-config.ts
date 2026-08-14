export type Periodicidad = 'MENSUAL' | 'QUINCENAL';
export type TipoConcepto = 'PORCENTAJE' | 'MONTO_FIJO';

export interface ConceptoDescuento {
  nombre: string;
  tipo: TipoConcepto;
  valor: number;
}

export interface PayrollConfig {
  id: string;
  umbralHorasExtra: number;
  multiplicadorHoraExtra: number;
  multiplicadorFeriado: number;
  periodicidad: Periodicidad;
  conceptosDescuento: ConceptoDescuento[];
}

export interface UpdatePayrollConfigRequest {
  umbralHorasExtra: number;
  multiplicadorHoraExtra: number;
  multiplicadorFeriado: number;
  periodicidad: Periodicidad;
  conceptosDescuento: ConceptoDescuento[];
}
