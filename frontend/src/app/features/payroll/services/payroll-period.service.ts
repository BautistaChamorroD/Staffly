import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { CreatePayrollPeriodRequest, PayrollPeriod } from '../models/payroll-period';

@Injectable({ providedIn: 'root' })
export class PayrollPeriodService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/payroll-periods`;

  list(): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[]>(this.baseUrl);
  }

  create(request: CreatePayrollPeriodRequest): Observable<PayrollPeriod> {
    return this.http.post<PayrollPeriod>(this.baseUrl, request);
  }

  close(id: string): Observable<PayrollPeriod> {
    return this.http.post<PayrollPeriod>(`${this.baseUrl}/${id}/close`, null);
  }

  reopen(id: string): Observable<PayrollPeriod> {
    return this.http.post<PayrollPeriod>(`${this.baseUrl}/${id}/reopen`, null);
  }
}
