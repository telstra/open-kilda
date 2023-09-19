import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpResponse, HttpErrorResponse, HttpInterceptor, HTTP_INTERCEPTORS, HttpHeaderResponse  } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { LoaderService } from '../services/loader.service';
import { CookieManagerService } from '../services/cookie-manager.service';
import { environment } from 'src/environments/environment';
import { Router } from '@angular/router';
import { local } from 'd3';
import { ToastrService } from 'ngx-toastr';
import { MessageObj } from '../constants/constants';

@Injectable()
export class AppAuthInterceptor implements HttpInterceptor {
    constructor(private appLoader: LoaderService, private cookieManager: CookieManagerService, private _router: Router, private toastr: ToastrService) {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        let requestToForward = request;
        const url_to_call = request.url;
        const url_arr = url_to_call.split('/');
        if (typeof(url_arr[2]) != 'undefined' && url_arr[2] == location.host) {
            const token = this.cookieManager.get('XSRF-TOKEN') as string;
            if (token !== null) {
                requestToForward = request.clone({ setHeaders: { 'X-XSRF-TOKEN': token } });
            }
        }
        return next.handle(requestToForward).pipe(catchError(err => {
            if (err.status === 401) {
                const msg = this.cookieManager.get('isLoggedOutInProgress') ? '' : MessageObj.session_expired ;
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
        }));
    }
}

export let AppAuthProvider = {
    provide: HTTP_INTERCEPTORS,
    useClass: AppAuthInterceptor,
    multi: true
};
