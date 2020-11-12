import { Directive, AfterViewInit,HostListener,  ElementRef, NgZone, OnDestroy, EventEmitter,Output, Input  } from '@angular/core';

declare var jQuery: any;

@Directive({
  selector: '[datetime-picker]'
})
export class DatetimepickerDirective implements AfterViewInit, OnDestroy{

  @HostListener('blur', ['$event'])
  onBlur(event: Event){ event.stopImmediatePropagation(); }
  @Input() maxDate:any;
  datePickerElement: any;
  @Output()changeVal = new EventEmitter;


  constructor(private eleRef: ElementRef,private zone: NgZone) {
    
   }

  ngAfterViewInit(){
    let nativeElement = this.eleRef.nativeElement;
   jQuery(nativeElement).datetimepicker({
    format:'Y/m/d H:i:s',
    onChangeDateTime:function(dp,$input){
      nativeElement.dispatchEvent(new Event("change"));
    },
    maxDate:this.maxDate
   });
  }

  ngOnDestroy(){
    jQuery(this.eleRef.nativeElement).datetimepicker('destroy');
  }

}
