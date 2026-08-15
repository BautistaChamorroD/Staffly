import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { CreateLeaveRequestBody, LeaveRequest, RejectLeaveRequestBody } from '../models/leave';

@Injectable({ providedIn: 'root' })
export class LeaveRequestService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/leave-requests`;

  list(filters: { employeeId?: string; estado?: string } = {}): Observable<LeaveRequest[]> {
    let params = new HttpParams();
    if (filters.employeeId) params = params.set('employeeId', filters.employeeId);
    if (filters.estado) params = params.set('estado', filters.estado);
    return this.http
      .get<LeaveRequest[] | { content: LeaveRequest[] }>(this.baseUrl, { params })
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }

  create(body: CreateLeaveRequestBody): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(this.baseUrl, body);
  }

  approve(id: string): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.baseUrl}/${id}/approve`, null);
  }

  reject(id: string, body: RejectLeaveRequestBody): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.baseUrl}/${id}/reject`, body);
  }

  cancel(id: string): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.baseUrl}/${id}/cancel`, null);
  }
}
