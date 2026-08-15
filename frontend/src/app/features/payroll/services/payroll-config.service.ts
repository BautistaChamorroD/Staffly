import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { PayrollConfig, UpdatePayrollConfigRequest } from '../models/payroll-config';

@Injectable({ providedIn: 'root' })
export class PayrollConfigService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/payroll-config`;
  private periodsUrl = `${environment.apiUrl}/payroll-periods`;

  get(): Observable<PayrollConfig> {
    return this.http.get<PayrollConfig>(this.baseUrl);
  }

  update(request: UpdatePayrollConfigRequest): Observable<PayrollConfig> {
    return this.http.put<PayrollConfig>(this.baseUrl, request);
  }

  hasClosedPeriods(): Observable<boolean> {
    return this.http
      .get<unknown>(`${this.periodsUrl}?estado=CERRADO`)
      .pipe(
        map((r) => {
          if (Array.isArray(r)) return r.length > 0;
          const p = r as { content?: unknown[]; totalElements?: number };
          return (p.totalElements ?? p.content?.length ?? 0) > 0;
        }),
      );
  }
}
