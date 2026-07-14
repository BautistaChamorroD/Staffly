export type EstadoEmpresa = 'ACTIVA' | 'SUSPENDIDA';

export interface Company {
  id: string;
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  estado: EstadoEmpresa;
  plan: string | null;
  fechaAlta: string;
}

export interface CreateCompanyRequest {
  nombre: string;
  razonSocial: string;
  pais: string;
  moneda: string;
  zonaHoraria: string;
  plan?: string;
  adminEmail: string;
}

export interface CreateCompanyResponse {
  company: Company;
  adminEmail: string;
  adminTemporaryPassword: string;
}

export interface UpdateCompanyRequest {
  nombre?: string;
  razonSocial?: string;
  pais?: string;
  moneda?: string;
  zonaHoraria?: string;
  plan?: string;
}

export interface UpdateCompanyStatusRequest {
  estado: EstadoEmpresa;
}
