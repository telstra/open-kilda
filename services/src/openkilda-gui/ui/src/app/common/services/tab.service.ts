import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TabService {
    private subject = new Subject<any>();

    setSelectedTab(tab: string) {
        this.subject.next({ text: tab });
    }

    getSelectedTab(): Observable<any> {
        return this.subject.asObservable();
    }

    clearSelectedTab() {
        this.subject.next();
    }
}