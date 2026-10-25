import { describe,it,expect,vi,beforeEach } from 'vitest';
import { render,screen,renderHook,act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable } from '../components/DataTable';
import { Badge,Field } from '../components/ui';
import { useTheme } from '../hooks';
describe('Record interactions',()=>{
 it('acts on the displayed record after filtering and sorting',async()=>{
  const choose=vi.fn(),user=userEvent.setup();
  render(<DataTable columns={[{key:'name',label:'Resource'}]} data={{content:[{id:'second',name:'Water'},{id:'first',name:'Blankets'}],page:0,size:8,totalElements:2,totalPages:1}} loading={false} q="" onQuery={()=>{}} onPage={()=>{}} onRow={choose}/>);
  await user.click(screen.getByRole('button',{name:'Water'}));
  expect(choose).toHaveBeenCalledWith({id:'second',name:'Water'});
 });
 it('renders resource names as text without interpreting HTML',()=>{
  const payload='<img src=x onerror=alert(1)>';
  const {container}=render(<DataTable columns={[{key:'name',label:'Resource'}]} data={{content:[{id:'1',name:payload}],page:0,size:8,totalElements:1,totalPages:1}} loading={false} q="" onQuery={()=>{}} onPage={()=>{}}/>);
  expect(screen.getByText(payload)).toBeInTheDocument();expect(container.querySelector('img')).toBeNull();
 });
 it('sends page and sort choices to the owning server query',async()=>{
  const page=vi.fn(),sort=vi.fn(),user=userEvent.setup();
  render(<DataTable columns={[{key:'name',label:'Resource',sortable:true}]} data={{content:[],page:0,size:8,totalElements:20,totalPages:3}} loading={false} q="" onQuery={()=>{}} onPage={page} onSort={sort}/>);
  await user.click(screen.getByRole('button',{name:'Next page'}));expect(page).toHaveBeenCalledWith(1);
  await user.click(screen.getByRole('button',{name:'Resource'}));expect(sort).toHaveBeenCalledWith('name');
 });
 it('distinguishes fully allocated from delivered',()=>{
  render(<><Badge status="FULFILLED"/><Badge status="DELIVERED"/></>);
  expect(screen.getByText('Fully allocated')).toBeInTheDocument();expect(screen.getByText('Delivered')).toBeInTheDocument();
 });
 it('associates form controls with their visible labels',()=>{
  render(<Field label="Quantity"><input type="number"/></Field>);
  expect(screen.getByLabelText('Quantity')).toBeInTheDocument();
 });
});
describe('Theme preferences',()=>{
 beforeEach(()=>localStorage.clear());
 it('uses the saved preference and persists a switch',()=>{
  localStorage.setItem('relief-theme','dark');
  const {result}=renderHook(()=>useTheme());expect(result.current.theme).toBe('dark');
  act(()=>result.current.toggle());expect(document.documentElement.dataset.theme).toBe('light');expect(localStorage.getItem('relief-theme')).toBe('light');
 });
});
