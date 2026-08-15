import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { Advance, CreateAdvanceRequest } from '../models/advance';

@Injectable({ providedIn: 'root' })
export class AdvanceService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/advances`;

  list(employeeId?: string): Observable<Advance[]> {
    let params = new HttpParams();
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<Advance[] | { content: Advance[] }>(this.baseUrl, { params }).pipe(
      map((r) => (Array.isArray(r) ? r : r.content)),
    );
  }

  create(request: CreateAdvanceRequest): Observable<Advance> {
    return this.http.post<Advance>(this.baseUrl, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
