import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  Company,
  CreateCompanyRequest,
  CreateCompanyResponse,
  UpdateCompanyRequest,
  UpdateCompanyStatusRequest,
} from '../models/company';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/companies`;

  list(): Observable<Company[]> {
    return this.http.get<Company[]>(this.baseUrl);
  }

  create(request: CreateCompanyRequest): Observable<CreateCompanyResponse> {
    return this.http.post<CreateCompanyResponse>(this.baseUrl, request);
  }

  update(id: string, request: UpdateCompanyRequest): Observable<Company> {
    return this.http.patch<Company>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateCompanyStatusRequest): Observable<Company> {
    return this.http.patch<Company>(`${this.baseUrl}/${id}/status`, request);
  }
}
