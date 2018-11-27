import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor,HTTP_INTERCEPTORS  } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LoaderService } from '../services/loader.service';


@Injectable()
export class AppAuthInterceptor implements HttpInterceptor {
    constructor(private appLoader:LoaderService) {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(catchError(err => {
            if (err.status === 401) {
                this.appLoader.show("Your session has been expired");
                localStorage.removeItem('flows');
                localStorage.removeItem('is2FaEnabled');
                localStorage.removeItem('userPermissions');
                localStorage.removeItem('user_id');
                localStorage.removeItem('username');
                location.reload(true);
            }
            return throwError(err);
        }))
    }
}

export let AppAuthProvider = {
    provide: HTTP_INTERCEPTORS,
    useClass: AppAuthInterceptor,
    multi: true
};