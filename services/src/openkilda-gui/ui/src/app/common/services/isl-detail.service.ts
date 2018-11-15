import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class IslDetailService {
    private subject = new Subject<any>();

    setSelectedItem(item: {}) {
        this.subject.next({ item: item });
    }

    getSelectedItem(): Observable<any> {
        return this.subject.asObservable();
    }
}