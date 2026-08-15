import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { AuditLogEntry, AuditLogFilters } from '../models/audit-log';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/audit-log`;

  list(filters: AuditLogFilters = {}): Observable<AuditLogEntry[]> {
    let params = new HttpParams();
    if (filters.entidad) params = params.set('entidad', filters.entidad);
    if (filters.entidadId) params = params.set('entidadId', filters.entidadId);
    if (filters.userId) params = params.set('userId', filters.userId);
    if (filters.desde) params = params.set('desde', filters.desde);
    if (filters.hasta) params = params.set('hasta', filters.hasta);
    return this.http
      .get<AuditLogEntry[] | { content: AuditLogEntry[] }>(this.baseUrl, { params })
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }
}
