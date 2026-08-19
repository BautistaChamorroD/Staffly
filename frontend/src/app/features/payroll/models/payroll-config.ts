export type Periodicidad = 'MENSUAL' | 'QUINCENAL' | 'SEMANAL';
export type TipoConcepto = 'PORCENTAJE' | 'MONTO_FIJO';
export type TipoUmbral = 'DIARIO' | 'SEMANAL';

export interface ConceptoDescuento {
  nombre: string;
  tipo: TipoConcepto;
  valor: number;
}

export interface PayrollConfig {
  id: string;
  umbralHorasExtra: number;
  tipoUmbral: TipoUmbral;
  multiplicadorHoraExtra: number;
  multiplicadorFeriado: number;
  periodicidad: Periodicidad;
  conceptosDescuento: ConceptoDescuento[];
}

export interface UpdatePayrollConfigRequest {
  umbralHorasExtra: number;
  tipoUmbral: TipoUmbral;
  multiplicadorHoraExtra: number;
  multiplicadorFeriado: number;
  periodicidad: Periodicidad;
  conceptosDescuento: ConceptoDescuento[];
}
