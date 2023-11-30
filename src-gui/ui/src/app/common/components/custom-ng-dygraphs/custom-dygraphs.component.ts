import {Component, ElementRef, Input, OnChanges, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {DygraphOptions} from './dygraphOptions';

declare const Dygraph: any;

@Component({
    selector: 'app-custom-dygraphs',
    templateUrl: './custom-dygraphs.component.html',
    styleUrls: ['./custom-dygraphs.component.css']
})
/**
 * Wrapper arround Dygraphs
 *
 * @class CustomDygraphsComponent
 */
export class CustomDygraphsComponent implements OnInit, OnChanges {
    @Input() public options: DygraphOptions;
    @Input() public data: any;
    @Input() public customVisibility: boolean;
    @Input() public noDataLabel: string;
    @ViewChild('chart') public chart: ElementRef;

    public loadingInProgress: boolean;
    public chartWidth: number;
    public chartHeight: number;
    public labels: string[];

    private _g: any;

    constructor() {
    }

    public ngOnInit() {
        this.noDataLabel = this.noDataLabel || 'NO DATA AVAILABLE';
        this.chartWidth = (this.options && this.options.width) || 640;
        this.chartHeight = (this.options && this.options.height) || 480;
    }

    /**
     * ngOnChanges
     * @method ngOnChanges
     * @return {void}
     */
    public ngOnChanges(changes: SimpleChanges) {

        if (!changes) {
            return;
        }

        if (!this.data || !this.data.length) {
            this.loadingInProgress = false;
            return;
        }

        this.loadingInProgress = true;

        const options = Object.assign({}, this.options);

        if (!options.width) {
            options.width = this.chartWidth;
        }
        if (!options.height) {
            options.height = this.chartHeight;
        }
        if (!options.legend) {
            options.legend = 'always';
        }

        const initialVisibility: boolean[] = [];
        if (options.labels) {
            if (this.customVisibility && options.labels.length > 1) {
                // options.labels[0] is always X axis
                this.labels = options.labels.slice(1);
            }

            options.labels.forEach((_) => {
                initialVisibility.push(true);
            });
        }
        if (options.labels) {
            options.visibility = initialVisibility;
        }

        setTimeout(() => {
            if (this._g) {
                this._g.destroy();
            }
            this._g = new Dygraph(this.chart.nativeElement,
                this.data,
                options
            );
            this.loadingInProgress = false;
        }, 500);
    }

    public changeVisibility(el: any) {
        const elem = el.currentTarget;
        this._g.setVisibility(parseInt(elem.id, 10), elem.checked);
    }
}
