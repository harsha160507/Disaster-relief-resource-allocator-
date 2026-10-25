import { useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { api, send } from '../lib/client';
import { has, label, number, roleLabels, type Page, type Profile, type Role, type Row } from '../model';
import { EntitySelect, Field, Modal, Notice, Submit } from '../components/ui';
import SpringCheck from '../components/supplied/SpringCheck';
export interface FormAction {kind:string;row?:Row;}
const formTitles:Record<string,string>={receive:'Receive stock',adjust:'Adjust stock','resource-types':'Add a resource',requests:'Create a relief request',locations:'Add a relief location',disasters:'Create a disaster response',organizations:'Add an organization',users:'Add a person',shipment:'Prepare a shipment','request-item':'Add a requested resource','affected-location':'Add an affected location','edit-organization':'Edit organization','edit-user':'Edit person'};
export function FormDialog({action,profile,onClose,onDone}:{action:FormAction;profile:Profile;onClose:()=>void;onDone:(message:string,row?:Row)=>void}){
 const {kind,row}=action;
 const [values,setValues]=useState<Row>(()=>({...row,organizationId:row?.organizationId||profile.organizationId||'',priority:'NORMAL',type:'FLOOD',issuer:import.meta.env.VITE_OIDC_AUTHORITY||'',active:row?.active??true,roles:row?.roles||['FIELD_AGENT'],perishable:false,quantity:'',change:'',resourceTypeId:'',destinationId:'',locationId:''}));
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[manifest,setManifest]=useState<Row[]>([]),[selected,setSelected]=useState<Record<string,Row>>({}),[allocationQuery,setAllocationQuery]=useState('');
 const set=(key:string,value:unknown)=>setValues(v=>({...v,[key]:value}));
 useEffect(()=>{if(kind!=='shipment'||!values.destinationId)return;const controller=new AbortController();api<Page>('/workspace/allocations?status=RESERVED&size=100&locationId='+values.destinationId+'&q='+encodeURIComponent(allocationQuery),{signal:controller.signal}).then(p=>setManifest(p.content.map(r=>r.allocation?{...r.allocation,...r}:r))).catch(e=>{if(e.name!=='AbortError')setError(e.message);});return()=>controller.abort();},[kind,values.destinationId,allocationQuery]);
 const field=(key:string,title:string,type='text',required=true,hint?:string)=><Field label={title} hint={hint}><input aria-label={title} name={key} type={type} value={values[key]??''} required={required} step={type==='number'?'0.001':undefined} min={type==='number'&&key!=='change'?'0.001':undefined} maxLength={key==='reference'?40:key==='displayName'?120:160} onChange={e=>set(key,e.target.value)}/></Field>;
 const entity=(key:string,title:string,collection:string)=><EntitySelect kind={collection} value={values[key]||''} label={title} onChange={value=>{set(key,value);if(key==='destinationId')setSelected({});}}/>;
 const org=has(profile)?entity('organizationId','Organization','organizations'):null;
 async function submit(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{
  let result:Row|undefined;
  if(kind==='receive')result=await send('/inventory/receipts',{locationId:values.locationId,resourceTypeId:values.resourceTypeId,quantity:Number(values.quantity)});
  if(kind==='adjust')result=await send('/inventory/'+row!.id,{change:Number(values.change)},'PATCH');
  if(kind==='resource-types')result=await send('/resource-types',{name:values.name,unitOfMeasure:values.unitOfMeasure,perishable:values.perishable});
  if(kind==='locations')result=await send('/locations',{name:values.name,address:values.address,organizationId:values.organizationId,latitude:values.latitude?Number(values.latitude):null,longitude:values.longitude?Number(values.longitude):null});
  if(kind==='disasters')result=await send('/disasters',{name:values.name,type:values.type,description:values.description||null,startedAt:new Date(values.startedAt||Date.now()).toISOString(),organizationId:values.organizationId});
  if(kind==='requests')result=await send('/requests',{reference:values.reference,disasterId:values.disasterId,requesterId:profile.id,destinationId:values.destinationId,priority:values.priority,notes:values.notes||null});
  if(kind==='request-item')result=await send('/requests/'+row!.id+'/items',{resourceTypeId:values.resourceTypeId,quantity:Number(values.quantity)});
  if(kind==='affected-location')result=await send('/disasters/'+row!.id+'/affected-locations/'+values.locationId,undefined,'PUT');
  if(kind==='shipment'){
   const ids=Object.keys(selected);if(!ids.length)throw new Error('Choose at least one reserved allocation.');
   result=await send('/shipments',{destinationId:values.destinationId,trackingReference:values.trackingReference,allocationIds:ids});
  }
  if(kind==='organizations'||kind==='edit-organization'){
   const body={name:values.name,code:values.code||null,contactEmail:values.contactEmail||null,...(kind==='edit-organization'?{active:values.active,version:row!.version}:{})};
   result=await send('/admin/organizations'+(row?'/'+row.id:''),body,row?'PUT':'POST');
  }
  if(kind==='users'||kind==='edit-user'){
   const administrator=values.roles.includes('SYSTEM_ADMIN');
   const body={displayName:values.displayName,email:values.email||null,organizationId:administrator?null:values.organizationId,roles:values.roles,...(kind==='edit-user'?{active:values.active,version:row!.version}:{issuer:values.issuer,subject:values.subject})};
   result=await send('/admin/users'+(row?'/'+row.id:''),body,row?'PUT':'POST');
  }
  onDone(kind==='requests'?'Draft created. Add resources, then submit it for review.':kind==='shipment'?'Shipment created. Reservations are held until dispatch.':'Changes saved successfully.',result);
 }catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <Modal title={formTitles[kind]||'Update record'} description={kind==='shipment'?'Group reserved allocations for one destination.':kind==='receive'?'Add incoming resources to a relief location.':undefined} onClose={onClose} busy={busy} wide={kind==='shipment'}>
 <form onSubmit={submit} className="operation-form">{error&&<Notice error>{error}</Notice>}
 {kind==='receive'&&<>{entity('locationId','Receiving location','locations')}{entity('resourceTypeId','Resource','resource-types')}{field('quantity','Quantity','number',true,'Use the resource’s recorded unit of measure. Receipt quantities must be positive.')}</>}
 {kind==='adjust'&&<><div className="form-context"><strong>{row!.resourceName||'Inventory record'}</strong><span>On hand: {number(row!.quantity)} · Reserved: {number(row!.reservedQuantity)}</span></div>{field('change','Quantity change','number',true,'Use a positive number to add stock or a negative number to reduce it. Reserved stock cannot be removed.')}</>}
 {kind==='resource-types'&&<>{field('name','Resource name')}{field('unitOfMeasure','Unit of measure','text',true,'For example: litres, kg, kits, or units.')}<SpringCheck label="Perishable resource" strike="none" checked={values.perishable} onChange={v=>set('perishable',v)} color="var(--accent)" doneOpacity={1}/></>}
 {kind==='locations'&&<>{org}{field('name','Location name')}{field('address','Address')}<div className="form-grid">{field('latitude','Latitude','number',false)}{field('longitude','Longitude','number',false)}</div></>}
 {kind==='disasters'&&<>{org}{field('name','Disaster name')}<Field label="Disaster type"><select value={values.type} onChange={e=>set('type',e.target.value)}>{['EARTHQUAKE','FLOOD','HURRICANE','WILDFIRE','TORNADO','DROUGHT','EPIDEMIC','OTHER'].map(value=><option key={value} value={value}>{label(value)}</option>)}</select></Field>{field('startedAt','Started at','datetime-local',false)}<Field label="Description"><textarea maxLength={2000} value={values.description||''} onChange={e=>set('description',e.target.value)}/></Field></>}
 {kind==='requests'&&<><div className="form-step-note"><span>1</span>Request details<ArrowRight size={14}/><span>2</span>Add resources<ArrowRight size={14}/><span>3</span>Submit for review</div>{field('reference','Request reference')}{entity('disasterId','Disaster','disasters')}{entity('destinationId','Destination','locations')}<Field label="Priority"><select value={values.priority} onChange={e=>set('priority',e.target.value)}>{['LOW','NORMAL','HIGH','CRITICAL'].map(value=><option key={value}>{value}</option>)}</select></Field><Field label="Notes"><textarea maxLength={2000} value={values.notes||''} onChange={e=>set('notes',e.target.value)}/></Field><Notice>The destination must be an affected location of the selected active disaster.</Notice></>}
 {kind==='request-item'&&<>{entity('resourceTypeId','Resource','resource-types')}{field('quantity','Requested quantity','number')}</>}
 {kind==='affected-location'&&entity('locationId','Affected location','locations')}
 {kind==='shipment'&&<>{entity('destinationId','Shipment destination','locations')}{field('trackingReference','Tracking reference')}{values.destinationId&&<><Field label="Find reserved allocations"><input placeholder="Search by request reference" value={allocationQuery} onChange={e=>setAllocationQuery(e.target.value)}/></Field><div className="manifest"><div className="manifest-header"><strong>Reserved allocations</strong><span>{Object.keys(selected).length} selected</span></div>{manifest.filter(a=>a.destinationId===values.destinationId).map(a=><label key={a.id} className="manifest-row"><input type="checkbox" checked={!!selected[a.id]} onChange={e=>setSelected(current=>{const next={...current};if(e.target.checked)next[a.id]=a;else delete next[a.id];return next;})}/><span><strong>{a.resourceName}</strong><small>{a.requestReference} · {a.locationName}</small></span><strong>{number(a.quantity)}</strong></label>)}{!manifest.length&&<p>No reserved allocations found. Allocate an approved request first.</p>}</div><small className="muted">Up to 100 allocations per manifest. Refine the reference search if needed.</small></>}</>}
 {['organizations','edit-organization'].includes(kind)&&<>{field('name','Organization name')}{field('code','Organization code','text',false)}{field('contactEmail','Contact email','email',false)}</>}
 {['users','edit-user'].includes(kind)&&<>{field('displayName','Display name')}{field('email','Email address','email',false)}{kind==='users'&&<>{field('issuer','Trusted issuer URL','url')}{field('subject','Verified identity subject','text',true,'Use the exact subject from your identity provider.')}</>}<fieldset className="role-fieldset"><legend>Roles</legend>{Object.entries(roleLabels).map(([role,title])=><label key={role}><input type="checkbox" checked={values.roles.includes(role)} onChange={e=>{const next=e.target.checked?role==='SYSTEM_ADMIN'?['SYSTEM_ADMIN']:[...values.roles.filter((r:string)=>r!=='SYSTEM_ADMIN'),role]:values.roles.filter((r:string)=>r!==role);set('roles',next);}}/>{title}</label>)}</fieldset>{!values.roles.includes('SYSTEM_ADMIN')&&entity('organizationId','Organization','organizations')}</>}
 {kind.startsWith('edit-')&&<label className="check-label"><input type="checkbox" checked={values.active} onChange={e=>set('active',e.target.checked)}/>Active {kind==='edit-user'?'user':'organization'}</label>}
 <footer className="form-footer"><button type="button" className="ui-btn" disabled={busy} onClick={onClose}>Cancel</button><Submit busy={busy}>{kind==='receive'?'Receive stock':kind==='requests'?'Create draft':kind==='shipment'?'Create shipment':'Save changes'}</Submit></footer></form></Modal>;
}
