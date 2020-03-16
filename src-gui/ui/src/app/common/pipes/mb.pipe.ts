import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'mb'
})
export class MbPipe implements PipeTransform {

  transform(value: any, args?: any): any {
    return (Math.floor(value/1000))+" Mbps";
  }

}
