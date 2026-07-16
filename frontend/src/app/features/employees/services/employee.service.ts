import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  CreateEmployeeRequest,
  Employee,
  EmployeeListFilters,
  UpdateEmployeeRequest,
  UpdateEmployeeStatusRequest,
} from '../models/employee';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/employees`;

  list(filters: EmployeeListFilters = {}): Observable<Employee[]> {
    let params = new HttpParams();
    if (filters.estadoLaboral) {
      params = params.set('estadoLaboral', filters.estadoLaboral);
    }
    if (filters.branchId) {
      params = params.set('branchId', filters.branchId);
    }
    if (filters.search) {
      params = params.set('search', filters.search);
    }
    return this.http.get<Employee[]>(this.baseUrl, { params });
  }

  create(request: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, request);
  }

  update(id: string, request: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}`, request);
  }

  updateStatus(id: string, request: UpdateEmployeeStatusRequest): Observable<Employee> {
    return this.http.patch<Employee>(`${this.baseUrl}/${id}/status`, request);
  }
}
