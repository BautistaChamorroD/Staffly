import { Rol } from './rol';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserSummary {
  id: string;
  email: string;
  rol: Rol;
  debeCambiarPassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserSummary;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface JwtClaims {
  sub: string;
  company_id: string | null;
  role: Rol;
  branch_ids: string[];
  type: 'access' | 'refresh';
  jti: string;
  iat: number;
  exp: number;
}
