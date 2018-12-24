import { BrowserModule, Title } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { DataTablesModule } from 'angular-datatables';
import { AngularFontAwesomeModule } from 'angular-font-awesome';
import { AppRoutingModule } from './app-routing.module';
import { NgSelectModule } from '@ng-select/ng-select';
import { ReactiveFormsModule } from '@angular/forms';
import { Select2Module } from "ng-select2-component";
import { HttpClientModule } from '@angular/common/http';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ToastrModule } from 'ngx-toastr';
import { NgDygraphsModule } from 'ng-dygraphs';
import { NgxSpinnerModule } from 'ngx-spinner';
import { ClipboardModule } from 'ngx-clipboard';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxTypeaheadModule } from 'ngx-typeahead';
import { AppComponent } from './app.component';
import { SidebarComponent } from './common/components/sidebar/sidebar.component';
import { HeaderComponent } from './common/components/header/header.component';
import { HomeComponent } from './modules/home/home.component';
import { TopologyComponent } from './modules/topology/topology.component';
import { IslComponent } from './modules/isl/isl.component';
import { SwitchesComponent } from './modules/switches/switches.component';
import { FooterComponent } from './common/components/footer/footer.component';
import { DatatableComponent } from './modules/isl/datatable/datatable.component';
import { FlowLayoutComponent, FlowComponent, FlowSearchComponent, FlowListComponent, FlowAddComponent, FlowEditComponent,FlowDetailComponent  } from './modules/flows';
import { UsermanagementComponent } from './modules/usermanagement/usermanagement.component';
import { UseractivityComponent } from './modules/useractivity/useractivity.component';
import { IslEditComponent } from './modules/isl/isl-edit/isl-edit.component';
import { IslListComponent } from './modules/isl/isl-list/isl-list.component';
import { BreadcrumbComponent } from './common/components/breadcrumb/breadcrumb.component';
import { UserListComponent } from './modules/usermanagement/users/user-list/user-list.component';
import { UserEditComponent } from './modules/usermanagement/users/user-edit/user-edit.component';
import { UserAddComponent } from './modules/usermanagement/users/user-add/user-add.component';
import { RoleListComponent } from './modules/usermanagement/roles/role-list/role-list.component';
import { RoleAddComponent } from './modules/usermanagement/roles/role-add/role-add.component';
import { RoleEditComponent } from './modules/usermanagement/roles/role-edit/role-edit.component';
import { PermissionListComponent } from './modules/usermanagement/permissions/permission-list/permission-list.component';
import { PermissionAddComponent } from './modules/usermanagement/permissions/permission-add/permission-add.component';
import { PermissionEditComponent } from './modules/usermanagement/permissions/permission-edit/permission-edit.component';
import { IslDetailComponent } from './modules/isl/isl-detail/isl-detail.component';
import { IslLayoutComponent } from './modules/isl/isl-layout/isl-layout.component';
import { MbPipe } from './common/pipes/mb.pipe';
import { SwitchidmaskPipe } from './common/pipes/switchidmask.pipe';
import { SwitchLayoutComponent } from './modules/switches/switch-layout/switch-layout.component';
import { SwitchListComponent } from './modules/switches/switch-list/switch-list.component';
import { AlertifyService } from './common/services/alertify.service';
import { DygraphComponent } from './common/dygraph/dygraph.component';
import { RoleAssignComponent } from './modules/usermanagement/roles/role-assign/role-assign.component';
import { RoleViewComponent } from './modules/usermanagement/roles/role-view/role-view.component';
import { PermissionViewComponent } from './modules/usermanagement/permissions/permission-view/permission-view.component';
import { PermissionAssignComponent } from './modules/usermanagement/permissions/permission-assign/permission-assign.component';
import { FlowPathComponent } from './modules/flows/flow-path/flow-path.component';
import { DatetimepickerDirective } from './common/directives/datetimepicker.directive';
import { FlowPathGraphComponent } from './modules/flows/flow-path-graph/flow-path-graph.component';
import { SwitchDetailComponent } from './modules/switches/switch-detail/switch-detail.component';
import { OtpComponent } from './common/components/otp/otp.component';
import { ModalComponent } from './common/components/modal/modal.component';
import { ChangepasswordComponent } from './common/components/changepassword/changepassword.component';
import { PortDetailsComponent } from './modules/switches/port-details/port-details.component';
import { RuleDetailsComponent } from './modules/switches/rule-details/rule-details.component';
import { FlowGraphComponent } from './modules/flows/flow-graph/flow-graph.component';
import { TopologyMenuComponent } from './modules/topology/topology-menu/topology-menu.component';
import { PortListComponent } from './modules/switches/port-list/port-list.component';
import { AffectedIslComponent } from './modules/topology/affected-isl/affected-isl.component';
import { FailedIslComponent } from './modules/topology/failed-isl/failed-isl.component';
import { UnidirectionalIslComponent } from './modules/topology/unidirectional-isl/unidirectional-isl.component';
import { SettingsComponent } from './modules/settings/settings.component';
import { IdentityServerComponent } from './modules/settings/identity-server/identity-server.component';
import { LinkStoreComponent } from './modules/settings/link-store/link-store.component';
import { ContextMenuModule } from 'ngx-contextmenu';
import { ModalconfirmationComponent } from './common/components/modalconfirmation/modalconfirmation.component';
import { LogoutComponent } from './common/components/logout/logout.component';
import { FlowDatatablesComponent } from './modules/flows/flow-datatables/flow-datatables.component';
import { SwitchDatatableComponent } from './modules/switches/switch-datatable/switch-datatable.component';
import { FlowContractsComponent } from './modules/flows/flow-contracts/flow-contracts.component';
import { ClickOutsideModule } from 'ng-click-outside';
import { ResetPasswordComponent } from './common/components/reset-password/reset-password.component';
import { AppAuthProvider } from './common/interceptors/app.auth.interceptor';
import { SessionComponent } from './modules/settings/session/session.component';

@NgModule({
  declarations: [
    AppComponent,
    SidebarComponent,
    HeaderComponent,
    HomeComponent,
    TopologyComponent,
    IslComponent,
    SwitchesComponent,
    FooterComponent,
    DatatableComponent,
    FlowLayoutComponent, FlowComponent,  FlowSearchComponent, FlowListComponent, FlowAddComponent, FlowEditComponent,FlowDetailComponent,
    UsermanagementComponent,
    UseractivityComponent,
    BreadcrumbComponent,
    IslEditComponent,
    IslListComponent,
    UserListComponent,
    UserEditComponent,
    UserAddComponent,
    RoleListComponent,
    RoleAddComponent,
    RoleEditComponent,
    PermissionListComponent,
    PermissionAddComponent,
    PermissionEditComponent,
    IslDetailComponent,
    IslLayoutComponent,
    MbPipe,
    SwitchidmaskPipe,
    SwitchLayoutComponent,
    SwitchListComponent,
    DygraphComponent,
    RoleAssignComponent,
    RoleViewComponent,
    PermissionViewComponent,
    PermissionAssignComponent,
    FlowPathComponent,
    DatetimepickerDirective,
    FlowPathGraphComponent,
    SwitchDetailComponent,
    OtpComponent,
    ModalComponent,
    ChangepasswordComponent,
    FlowGraphComponent,
    TopologyMenuComponent,
    PortDetailsComponent,
    RuleDetailsComponent,
    PortListComponent,
    AffectedIslComponent,
    FailedIslComponent,
    UnidirectionalIslComponent,
    SettingsComponent,
    IdentityServerComponent,
    LinkStoreComponent,
    ModalconfirmationComponent,
    LogoutComponent,
    FlowDatatablesComponent,
    SwitchDatatableComponent,
    FlowContractsComponent,
    ResetPasswordComponent,
    SessionComponent
  ],
  imports: [
    HttpClientModule,
    BrowserModule,
    NgDygraphsModule,
    AppRoutingModule,
    AngularFontAwesomeModule,
    DataTablesModule,
    NgSelectModule,
    ReactiveFormsModule,
    Select2Module,
    ClipboardModule,
    NgbModule,
    NgxTypeaheadModule,
    BrowserAnimationsModule, // required animations module
    ToastrModule.forRoot({
      timeOut:4000,
      positionClass: 'toast-top-right',
      preventDuplicates: true,
    }),
    NgxSpinnerModule,
    ContextMenuModule.forRoot({
      autoFocus: true
    }),
    ClickOutsideModule
  ],
  providers: [SwitchidmaskPipe, AlertifyService, Title,AppAuthProvider],
  bootstrap: [AppComponent],
  entryComponents:[
    OtpComponent,
    ModalComponent,
    ModalconfirmationComponent,
    ChangepasswordComponent,
    AffectedIslComponent,
    FlowGraphComponent,
    FlowPathGraphComponent,
    FlowContractsComponent,
    ResetPasswordComponent
  ]
})
export class AppModule { }
