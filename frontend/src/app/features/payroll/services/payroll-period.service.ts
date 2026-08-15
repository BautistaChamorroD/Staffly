import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { CreatePayrollPeriodRequest, PayrollPeriod } from '../models/payroll-period';

@Injectable({ providedIn: 'root' })
export class PayrollPeriodService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/payroll-periods`;

  list(): Observable<PayrollPeriod[]> {
    return this.http.get<PayrollPeriod[] | { content: PayrollPeriod[] }>(this.baseUrl).pipe(
      map((r) => (Array.isArray(r) ? r : r.content)),
    );
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
