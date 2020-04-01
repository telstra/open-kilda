import { Component, OnInit, OnChanges } from "@angular/core";
import {
  Router,
  ActivatedRoute,
  NavigationEnd,
  Params,
  PRIMARY_OUTLET
} from "@angular/router";
import { filter } from "rxjs/operators";
import { Location } from "@angular/common";

interface IBreadcrumb {
  label: string;
  params?: Params;
  url: string,
  links: any,
}

@Component({
  selector: "app-breadcrumb",
  templateUrl: "./breadcrumb.component.html",
  styleUrls: ["./breadcrumb.component.css"]
})
export class BreadcrumbComponent implements OnInit {
  public breadcrumbs: IBreadcrumb[];
  public currentRoute : any;
  constructor(private activatedRoute: ActivatedRoute, private router: Router,private location:Location) {
    this.breadcrumbs = [];
  }

  ngOnInit() {
    const ROUTE_DATA_BREADCRUMB: string = "breadcrumb";

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd)) .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        // set breadcrumbs
        let tempRoute : any = event;
        this.currentRoute = tempRoute.url;
        let root: ActivatedRoute = this.activatedRoute.root;
        this.breadcrumbs = this.filterBreadCrumbs(this.getBreadcrumbs(root));
      });  
  }

  private getBreadcrumbs(
    route: ActivatedRoute,
    url: string = "",
    breadcrumbs: IBreadcrumb[] = []
  ): IBreadcrumb[] {
    const ROUTE_DATA_BREADCRUMB: string = "breadcrumb";

    // get the child routes
    let children: ActivatedRoute[] = route.children;

    //return if there are no more children
    if (children.length === 0) {
      return breadcrumbs;
    }

    //iterate over each children
    for (let child of children) {
      //verify primary route
      if (child.outlet !== PRIMARY_OUTLET) {
        continue;
      }

      //verify the custom data property "breadcrumb" is specified on the route
      if (!child.snapshot.data.hasOwnProperty(ROUTE_DATA_BREADCRUMB)) {
        return this.getBreadcrumbs(child, url, breadcrumbs);
      }

      //get the route's URL segment
      let routeURL: string = child.snapshot.url
        .map(segment => segment.path)
        .join("/");

      //append route URL to URL
      url += `/${routeURL}`;

      let patt = new RegExp("{[a-z]+}");
      let breadcrumbLabel = child.snapshot.data[ROUTE_DATA_BREADCRUMB];

      let breadcrumbLinks = [];

      if(typeof child.snapshot.data.links !== 'undefined' ){
        breadcrumbLinks = child.snapshot.data.links;
      }

      /** manipulating breadcrumbs for dynamic params */
      let dynamicBreadCrumb = patt.test(
        child.snapshot.data[ROUTE_DATA_BREADCRUMB]
      );

      if (dynamicBreadCrumb) {
        for (let param in child.snapshot.params) {
          let re = new RegExp("{" + param + "}", "g");
          breadcrumbLabel = breadcrumbLabel.replace(
            re,
            child.snapshot.params[param]
          );
          if(breadcrumbLinks.length > 0){
            breadcrumbLinks.forEach((v,i)=>{
             v.link=v.link.replace(re, child.snapshot.params[param]);
             breadcrumbLinks[i] = v;
            });
          }
        }
      }

      let breadcrumb: IBreadcrumb = {
        label: breadcrumbLabel,
        params: child.snapshot.params,
        url: url,
        links : breadcrumbLinks
      };
      breadcrumbs.push(breadcrumb);

      //recursive
      return this.getBreadcrumbs(child, url, breadcrumbs);
    }
    //we should never get here, but just in case
    return breadcrumbs;
  }

  private filterBreadCrumbs(breadcrumbs){
      return breadcrumbs.filter(element=>{
        return element.label;
      })

  }

  

 
}
