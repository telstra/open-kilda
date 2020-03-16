import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { HomeComponent } from './modules/home/home.component';
import { TopologyComponent } from './modules/topology/topology.component';
import { IslComponent } from './modules/isl/isl.component';
import { IslLayoutComponent } from './modules/isl/isl-layout/isl-layout.component';
import { IslDetailComponent } from './modules/isl/isl-detail/isl-detail.component';
import { SwitchLayoutComponent, SwitchesComponent,SwitchDetailComponent } from './modules/switches';
import { FlowLayoutComponent, FlowComponent, FlowDetailComponent, FlowAddComponent, FlowEditComponent } from './modules/flows';
import { PortDetailsComponent } from './modules/switches/port-details/port-details.component';
import { UsermanagementComponent } from './modules/usermanagement/usermanagement.component';
import { UseractivityComponent } from './modules/useractivity/useractivity.component';
import { SettingsComponent } from './modules/settings/settings.component';
import { LogoutComponent } from './common/components/logout/logout.component';
import { SessionComponent } from './modules/settings/session/session.component';
import { NetworkpathComponent } from './modules/networkpath/networkpath.component';

const routes: Routes = [{
		path: '',
		redirectTo: '/home', 
		pathMatch: 'full'
	},{
	  	path: 'home', 
		component:  HomeComponent,
		data: { title: 'HOME' } 
  	}, {
	  	path: 'topology', 
		component:  TopologyComponent,
		data:{
			breadcrumb: "Topology",
			title: 'tOPOLOGY'
		}
  	}, {
		path:'flows',
		component:FlowLayoutComponent,
		data:{
			breadcrumb: "Flows"
		},
		children:[
			{
				path:"",
				component:FlowComponent,
				data:{
					breadcrumb: ""
				},
			},
			{
				path:"add-new",
				component: FlowAddComponent,
				data:{
					breadcrumb: "New Flow"
				}
			},
			{
				path:"details/:id",
				component: FlowDetailComponent,
				data:{
					breadcrumb: "{id}",
					links:[{
							"label":"Edit",
							"link":"/flows/edit/{id}"
						}
					]
				}
			},
			{
				path:"edit/:id",
				component: FlowEditComponent,
				data:{
					breadcrumb: "{id}"
				}
			},
			
		]
	}, {
	  	path: 'isl', 
	  	component: IslLayoutComponent,
	  	data:{
			breadcrumb: "ISL"
		},
		children:[{
				path:"",
				component:IslComponent,
				data:{
					breadcrumb: ""
				},
			},{
				path:"switch/isl/:src_switch/:src_port/:dst_switch/:dst_port",
				component: IslDetailComponent,
				data:{
					breadcrumb: "ISL Details"
				}
			}
		]
  	}, {
	  	path: 'switches', 
		  component:  SwitchLayoutComponent,
		  data: {
			breadcrumb:'Switches'
		  },
		  children:[
			  {
				  path:"",
				  component: SwitchesComponent,
				  data:{
					  breadcrumb:""
				  }
			  },{
				path:"details/:id",
				component: SwitchDetailComponent,
				data:{
					breadcrumb: "{id}",
				},
				children:[
					{
				path:"port/:port",
				component: PortDetailsComponent,
				data:{
					breadcrumb: "{port}",
				}
			}
				]
			},
		  ]
	},{
		path: 'usermanagement', 
		component:  UsermanagementComponent,
		data:{
			breadcrumb: "User Management"
		}
	},{
		path: 'useractivity',
		component: UseractivityComponent,
		data:{
			breadcrumb: "User Activity"
		}
	},
	{
		path: 'storesetting',
		component: SettingsComponent,
		data:{
			breadcrumb: "Store Settings"
		}
	},
	{
		path: 'application-setting',
		component: SessionComponent,
		data:{
			breadcrumb: "Application Settings"
		}
	},
	{
		path: 'logout',
		component: LogoutComponent
	},
	{
		path: 'networkpath', 
	  component:  NetworkpathComponent,
	  data:{
		  breadcrumb: "Available Path",
		  title: 'Available Path'
	  }
	},
	{ 
		path: '**', 
		redirectTo: 'home'
	}
];

@NgModule({
  imports: [RouterModule.forRoot(routes,{scrollPositionRestoration:"top"})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
