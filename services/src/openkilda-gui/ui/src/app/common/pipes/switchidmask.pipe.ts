import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'switchidmask'
})
export class SwitchidmaskPipe implements PipeTransform {

  transform(value: any = null, pattern: string): any {
    if(value ){
      var prefix = "SW";
			if(pattern == 'legacy'){
        return prefix+value.replace(/:/g , "").toUpperCase();
			}else{
        return this.addCharacter(value,2).join(':').substring(3).toLowerCase();
      }
    }
  }


  addCharacter(str, n): any {
    var ret = [];
    
    for(var i = 0, len = str.length; i < len; i += n) {
       ret.push(str.substr(i, n))
    }
    return ret;
  }

}
