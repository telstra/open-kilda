import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent,HttpResponse,HttpErrorResponse, HttpInterceptor,HTTP_INTERCEPTORS, HttpHeaderResponse  } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { LoaderService } from '../services/loader.service';
import { CookieManagerService } from '../services/cookie-manager.service';
import { environment } from 'src/environments/environment';
import { Router } from '@angular/router';

@Injectable()
export class AppAuthInterceptor implements HttpInterceptor {
    constructor(private appLoader:LoaderService, private cookieManager:CookieManagerService,private _router: Router) {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).pipe(catchError(err => {
            if (err.status === 401) {
                let msg = this.cookieManager.get('isLoggedOutInProgress') ? "": "Your session has been expired" ;
                this.appLoader.show(msg);
                localStorage.removeItem('flows');
                localStorage.removeItem('is2FaEnabled');
                localStorage.removeItem('userPermissions');
                localStorage.removeItem('user_id');
                localStorage.removeItem('username');
                this.cookieManager.delete('isLoggedOutInProgress');
                this._router.navigate(['/logout']);
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