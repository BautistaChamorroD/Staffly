import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Availability, CreateAvailabilityRequest, UpdateAvailabilityRequest } from '../models/availability';

@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private http = inject(HttpClient);

  private base(employeeId: string): string {
    return `${environment.apiUrl}/employees/${employeeId}/availability`;
  }

  list(employeeId: string): Observable<Availability[]> {
    return this.http.get<Availability[]>(this.base(employeeId));
  }

  create(employeeId: string, request: CreateAvailabilityRequest): Observable<Availability> {
    return this.http.post<Availability>(this.base(employeeId), request);
  }

  update(employeeId: string, id: string, request: UpdateAvailabilityRequest): Observable<Availability> {
    return this.http.patch<Availability>(`${this.base(employeeId)}/${id}`, request);
  }

  delete(employeeId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base(employeeId)}/${id}`);
  }
}
