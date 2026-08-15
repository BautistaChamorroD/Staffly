import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { LeaveType } from '../models/leave';

@Injectable({ providedIn: 'root' })
export class LeaveTypeService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/leave-types`;

  list(): Observable<LeaveType[]> {
    return this.http
      .get<LeaveType[] | { content: LeaveType[] }>(this.baseUrl)
      .pipe(map((r) => (Array.isArray(r) ? r : r.content)));
  }

  create(request: Partial<LeaveType>): Observable<LeaveType> {
    return this.http.post<LeaveType>(this.baseUrl, request);
  }

  update(id: string, request: Partial<LeaveType>): Observable<LeaveType> {
    return this.http.patch<LeaveType>(`${this.baseUrl}/${id}`, request);
  }
}
