import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { Payslip, VoidPayslipRequest } from '../models/payslip';

@Injectable({ providedIn: 'root' })
export class PayslipService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/payslips`;

  list(filters: { employeeId?: string; payrollPeriodId?: string } = {}): Observable<Payslip[]> {
    let params = new HttpParams();
    if (filters.employeeId) params = params.set('employeeId', filters.employeeId);
    if (filters.payrollPeriodId) params = params.set('payrollPeriodId', filters.payrollPeriodId);
    return this.http.get<Payslip[] | { content: Payslip[] }>(this.baseUrl, { params }).pipe(
      map((r) => (Array.isArray(r) ? r : r.content)),
    );
  }

  voidAndAdjust(id: string, request: VoidPayslipRequest): Observable<Payslip> {
    return this.http.post<Payslip>(`${this.baseUrl}/${id}/void`, request);
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/pdf`, { responseType: 'blob' });
  }

  markPaid(id: string): Observable<Payslip> {
    return this.http.patch<Payslip>(`${this.baseUrl}/${id}/mark-paid`, null);
  }
}
