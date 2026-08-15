import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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
    return this.http.get<Payslip[]>(this.baseUrl, { params });
  }

  me(): Observable<Payslip[]> {
    return this.http.get<Payslip[]>(`${this.baseUrl}/me`);
  }

  voidAndAdjust(id: string, request: VoidPayslipRequest = {}): Observable<Payslip> {
    return this.http.post<Payslip>(`${this.baseUrl}/${id}/void`, request);
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/pdf`, { responseType: 'blob' });
  }
}
