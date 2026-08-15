import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ExportFormat, HoursWorkedRow, PayrollCostRow, PendingAdvanceRow, ReportType } from '../models/report';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/reports`;

  hoursWorked(filters: { branchId?: string; desde?: string; hasta?: string } = {}): Observable<HoursWorkedRow[]> {
    let params = new HttpParams();
    if (filters.branchId) params = params.set('branchId', filters.branchId);
    if (filters.desde) params = params.set('desde', filters.desde);
    if (filters.hasta) params = params.set('hasta', filters.hasta);
    return this.http
      .get<HoursWorkedRow[] | { content: HoursWorkedRow[] }>(`${this.baseUrl}/hours-worked`, { params })
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }

  payrollCost(filters: { branchId?: string; desde?: string; hasta?: string } = {}): Observable<PayrollCostRow[]> {
    let params = new HttpParams();
    if (filters.branchId) params = params.set('branchId', filters.branchId);
    if (filters.desde) params = params.set('desde', filters.desde);
    if (filters.hasta) params = params.set('hasta', filters.hasta);
    return this.http
      .get<PayrollCostRow[] | { content: PayrollCostRow[] }>(`${this.baseUrl}/payroll-cost`, { params })
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }

  pendingAdvances(): Observable<PendingAdvanceRow[]> {
    return this.http
      .get<PendingAdvanceRow[] | { content: PendingAdvanceRow[] }>(`${this.baseUrl}/pending-advances`)
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }

  export(report: ReportType, format: ExportFormat, filters: Record<string, string> = {}): Observable<Blob> {
    let params = new HttpParams().set('format', format);
    for (const [key, value] of Object.entries(filters)) {
      if (value) params = params.set(key, value);
    }
    return this.http.get(`${this.baseUrl}/${report}/export`, { params, responseType: 'blob' });
  }
}
