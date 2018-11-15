import { Injectable } from '@angular/core';
declare let alertify: any;

@Injectable()
export class AlertifyService{
    constructor(){}

    confirm(title: string, message: string, okCallback: () => any){
        alertify.confirm(message, function(e){
            if(e){
                okCallback();
            }else{
                console.log(e);
                //Do nothing
            }
        })
        .setHeader(title)
        .set('labels', {ok:'Yes', cancel:'No'})
        .set( {transition:'slide' })   
        .set('position', 'top-center')
        .show(true, 'custom_class');
    }

    success(title: string, message: string){
        alertify.success(message).setHeader(title); 
    }
    
    error(title: string, message: string){
        alertify.error(message).setHeader(title); 
    }

    warning(title: string, message: string){
        alertify.warning(message).setHeader(title); 
    }

    message(message: string){
        alertify.message(message); 
    }

    prompt(){
        alertify.prompt().setContent('<app-otp></app-otp>').show(); 
    }

    
}