const state={schoolId:localStorage.schoolId||1,user:localStorage.user||'',password:localStorage.password||'',students:[],classes:[],types:[],receipts:[]};
let pendingToggleStudentId = null;
let pendingToggleNewState = null;
const $=s=>document.querySelector(s), esc=v=>String(v||'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money=n=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(Number(n||0));

async function api(path,opts={}){
  if(!state.user) throw new Error('Connect your school first.');
  const res = await fetch('/api'+path, {...opts, headers: {...(opts.headers||{}), Authorization: 'Basic '+btoa(`${state.user}:${state.password}`), 'Content-Type':'application/json'}});
  if(!res.ok){ let m='Request failed'; try{ m=(await res.json()).message||m }catch{} throw new Error(m) }
  return res.status===204?null:res.json();
}
function schoolQuery(){ return `schoolId=${state.schoolId}` }

async function loadBase(){
  [state.students,state.classes,state.types,state.receipts]=await Promise.all([
    api(`/students?${schoolQuery()}`),
    api(`/classes?${schoolQuery()}`),
    api(`/fee-types?${schoolQuery()}`),
    api(`/fees?${schoolQuery()}`)
  ]);
  // attempt to fetch school info to display friendly name
  try{
    const school = await api(`/schools/${state.schoolId}`);
    state.schoolName = school.name;
    const el = document.getElementById('schoolNameDisplay'); if(el) el.value = state.schoolName;
    const ci = document.getElementById('connectedInfo'); if(ci) ci.textContent = `Connected as ${state.user} — ${state.schoolName}`;
  }catch(e){ console.warn('Could not load school info', e); }
  renderAll();
  // render receipts list and recent payments
  renderReceipts();
  // refresh receipts student list if class selected
  const fc = $('#feeClass'); if(fc && fc.value) fc.dispatchEvent(new Event('change'));
}

// receipt rendering helpers
function receiptRow(r, all=false){
  return `<tr data-receipt-id="${r.id}"><td><b>${esc(r.receiptNumber)}</b></td><td>${esc(r.studentName)}</td><td>${r.paymentDate}</td>${all?`<td>${(r.items||[]).map(i=>esc(i.feeTypeName)).join(', ')}</td>`:''}<td><b>${money(r.totalAmount)}</b></td><td><span class="badge ${String(r.status).toLowerCase()}">${esc(String(r.status))}</span></td><td><button type="button" class="secondary print-btn" data-id="${r.id}">Print</button></td></tr>`
}

function renderReceipts(){
  try{
    const rows = state.receipts && state.receipts.length ? state.receipts.map(r=>receiptRow(r,true)).join('') : '';
    $('#receiptRows').innerHTML = rows || '<tr><td colspan="7" class="empty">No receipts found.</td></tr>';
    // attach print handlers
    document.querySelectorAll('.print-btn').forEach(b=>b.addEventListener('click', ()=>{ const id=b.dataset.id; printReceipt(Number(id)); }));

function printReceipt(id){
  const r = (state.receipts||[]).find(x=>Number(x.id)===Number(id));
  if(r){ printWindowForReceipt(r); return; }
  // fetch single receipt if not cached
  api(`/fees/${id}?${schoolQuery()}`).then(res=>{ printWindowForReceipt(res); }).catch(e=>alert(e.message));
}

    // recent payments on dashboard
    if($('#recentPayments')){
      $('#recentPayments').innerHTML = (state.receipts && state.receipts.length ? state.receipts.slice(0,5).map(receiptRow).join('') : '<tr><td colspan="5" class="empty">No payments yet.</td></tr>');
    }
    // summary counts
    $('#receiptCount') && (document.getElementById('receiptCount').textContent = state.receipts? state.receipts.length : '0');
    const total = (state.receipts||[]).reduce((s,r)=>s+(Number(r.totalAmount)||0),0);
    $('#totalCollected') && (document.getElementById('totalCollected').textContent = money(total));
    $('#studentCount') && (document.getElementById('studentCount').textContent = (state.students||[]).length);
  }catch(e){ console.error('renderReceipts', e); }
}

// reports
async function runReport(){
  if(!state.user) return;
  try{
    const v = document.getElementById('reportPeriod')?.value;
    if(!v) return;
    const [y,m] = v.split('-');
    if(!y) return;
    const classId = document.getElementById('reportClass')?.value || '';
    const classParam = classId? `&classId=${classId}` : '';
    const data = await api(`/reports/fees/monthly?${schoolQuery()}&year=${y}&month=${Number(m)}${classParam}`);
    document.getElementById('reportTotal') && (document.getElementById('reportTotal').textContent = money(data.total));
    document.getElementById('reportCaption') && (document.getElementById('reportCaption').textContent = data.period);
    const max = Math.max(...(data.breakdown.map(x=>Number(x.amount))||[0]),1);
    document.getElementById('reportBreakdown') && (document.getElementById('reportBreakdown').innerHTML = data.breakdown.map(x=>`<div class="breakdown-row"><b>${esc(x.feeTypeName)}</b><div class="bar"><i style="width:${Number(x.amount)/max*100}%"></i></div><b>${money(x.amount)}</b></div>`).join('')||'<p class="empty">No active collections for this month.</p>');
  }catch(e){ alert(e.message); }
}


function renderAll(){
  // sort classes
  state.classes.sort((a,b)=>{const an=Number(a.name),bn=Number(b.name);if(!isNaN(an)&&!isNaN(bn)&&an!==bn) return an-bn;const n=a.name.localeCompare(b.name,undefined,{numeric:true});if(n!==0) return n;return (a.section||'').localeCompare(b.section||'');});

  const classOpts = state.classes.map(c=>`<option value="${c.id}">${esc(c.name)}${c.section?'-'+esc(c.section):''}</option>`).join('');
  $('#newClass').innerHTML = '<option value="">Choose a class</option>' + (classOpts||'') + '<option value="__new">+ Add new class...</option>';
  $('#classFilter').innerHTML = '<option value="">All classes</option>' + (classOpts||'');
  const feeClassEl = $('#feeClass'); if(feeClassEl) feeClassEl.innerHTML = '<option value="">Choose a class</option>' + (classOpts||'');
  const reportClassEl = document.getElementById('reportClass'); if(reportClassEl) reportClassEl.innerHTML = '<option value="">All classes</option>' + (classOpts||'');

  // students list
  const rows = state.students.map(s=>{
    const clsName = s.schoolClass?.name ? esc(s.schoolClass.name) : '';
    const clsSection = s.schoolClass?.section ? esc(s.schoolClass.section) : '';
    const classDisplay = clsName + (clsSection ? ('–' + clsSection) : '');
    const actionBtn = `<button type="button" class="secondary toggle-active" data-id="${s.id}" data-active="${s.active}">${s.active? 'Deactivate' : 'Activate'}</button>`;
    return `<tr><td><b>${esc(s.name)}</b></td><td>${esc(s.admissionNumber)}</td><td>${classDisplay}</td><td>${esc(s.fatherName||s.guardianName||'—')}</td><td>${esc(s.phone||'—')}</td><td><span class="badge ${s.active?'active':'cancelled'}">${s.active?'ACTIVE':'INACTIVE'}</span></td><td>${actionBtn}</td></tr>`
  }).join('');
  $('#studentRows').innerHTML = rows || '<tr><td colspan="6" class="empty">No students found.</td></tr>';

  // reset feeItems and summary if present
  if($('#feeItems')){
    $('#feeItems').innerHTML = '';
    if(state.types && state.types.length) addFeeRow();
    else $('#summaryItems').innerHTML = '<p class="muted">Add fee items to begin.</p>';
    summary();
  }
}

// summary updates total and listing
function summary(){
  const rows = [...document.querySelectorAll('.fee-row')];
  const items = rows.map(r=>{
    const sel = r.querySelector('select.fee-type');
    const txt = sel && sel.selectedOptions[0] ? sel.selectedOptions[0].text : '';
    const amt = Number(r.querySelector('input.fee-amount')?.value)||0;
    return {name: txt, amount: amt};
  }).filter(x=>x.amount>0 && x.name && x.name!=='Fee type');
  const total = items.reduce((s,x)=>s+x.amount,0);
  $('#feeTotal').textContent = money(total);
  if(items.length){
    $('#summaryItems').innerHTML = items.map(i=>`<div class="summary-line"><span>${esc(i.name)}</span><b>${money(i.amount)}</b></div>`).join('');
  } else {
    $('#summaryItems').innerHTML = '<p class="muted">Add fee items to begin.</p>';
  }
}

// event wiring
document.addEventListener('click',e=>{const t=e.target; if(t.matches('[data-view],[data-go]')){ e.preventDefault(); const name=t.dataset.view||t.dataset.go; document.querySelectorAll('.view').forEach(v=>v.classList.remove('active')); document.querySelectorAll('.nav-link[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===name)); $(`#${name}View`).classList.add('active'); // page title and sidebar
    const titles={dashboard:'Overview',students:'Students',collect:'Collect fees',receipts:'Receipts',reports:'Reports'}; $('#pageTitle')&&(document.getElementById('pageTitle').textContent = titles[name]||''); document.querySelector('.sidebar')&&document.querySelector('.sidebar').classList.remove('open'); if(name==='receipts') renderReceipts(); if(name==='reports') runReport(); }
 if(t.id==='openStudent') $('#studentDialog').showModal();
});

// settings/profile buttons (open connection dialog)
$('#settingsBtn')?.addEventListener('click', ()=>{ $('#connectionDialog').showModal(); });
$('#profileBtn')?.addEventListener('click', ()=>{ $('#connectionDialog').showModal(); });

// refresh receipts button
$('#refreshReceipts')?.addEventListener('click', async ()=>{ try{ state.receipts = await api(`/fees?${schoolQuery()}`); renderReceipts(); }catch(e){ alert(e.message) } });

// connection
$('#connectionForm').addEventListener('submit',async e=>{ e.preventDefault(); const sub = e.submitter && e.submitter.value ? e.submitter.value : null; if(sub === 'cancel'){ $('#connectionDialog').close(); return; } // perform connect
  const loginType = (document.getElementById('loginType') && document.getElementById('loginType').value) || 'school';
  // if admin and numeric id provided in the hidden input, use it; otherwise keep default
  const sidInput = document.getElementById('schoolId');
  const sid = sidInput && sidInput.value ? Number(sidInput.value) : state.schoolId || 1;
  state.schoolId = sid;
  state.user = $('#username').value; state.password = $('#password').value; localStorage.schoolId = state.schoolId; localStorage.user = state.user; localStorage.password = state.password; try{ await loadBase(); $('#connectionDialog').close(); $('#profileInitial').textContent = state.user[0].toUpperCase(); $('#profileName').textContent = state.user; const ci = document.getElementById('connectedInfo'); if(ci) ci.textContent = `Connected as ${state.user} — ${state.schoolName||('School #'+state.schoolId)}`; }catch(err){ alert(err.message); }});
// adapt schoolId requirement when login type changes
document.getElementById('loginType')?.addEventListener('change', e=>{ const v = e.target.value; const sidWrapper = document.getElementById('schoolIdInputWrapper'); if(!sidWrapper) return; if(v==='admin'){ sidWrapper.classList.remove('hidden'); } else { sidWrapper.classList.add('hidden'); } });
// allow editing school id
document.getElementById('editSchoolId')?.addEventListener('click', ()=>{ const w = document.getElementById('schoolIdInputWrapper'); if(w) w.classList.toggle('hidden'); });
// logout
$('#logoutBtn')?.addEventListener('click', ()=>{ localStorage.removeItem('user'); localStorage.removeItem('password'); localStorage.removeItem('schoolId'); state.user = ''; state.password = ''; state.schoolId = 1; state.schoolName = undefined; $('#profileInitial').textContent = ''; $('#profileName').textContent = ''; const ci = document.getElementById('connectedInfo'); if(ci) ci.textContent = 'Not connected'; $('#connectionDialog').showModal(); });

// report button
$('#runReport')?.addEventListener('click', runReport);


// new class panel
$('#newClass')?.addEventListener('change',()=>{ if($('#newClass').value==='__new') $('#newClassPanel').classList.remove('hidden'); else $('#newClassPanel').classList.add('hidden'); });
$('#createClassBtn').addEventListener('click',async ()=>{ const name=$('#newClassName').value.trim(); const section=$('#newClassSection').value.trim(); if(!name) return alert('Enter class name'); try{ const created = await api(`/classes?${schoolQuery()}`, {method:'POST', body: JSON.stringify({name, section, active:true})}); await loadBase(); $('#newClass').value = created.id; $('#newClassPanel').classList.add('hidden'); $('#newClassName').value=''; $('#newClassSection').value=''; alert('Class created'); }catch(e){ alert(e.message); } });
$('#cancelCreateClass').addEventListener('click',()=>{ $('#newClassPanel').classList.add('hidden'); $('#newClassName').value=''; $('#newClassSection').value=''; $('#newClass').value=''; });

// student form
$('#studentForm').addEventListener('submit',async e=>{ e.preventDefault(); let classVal = $('#newClass').value; if(String(classVal).startsWith('__seed_')){ const className = classVal.replace('__seed_',''); const created = await api(`/classes?${schoolQuery()}`,{method:'POST', body: JSON.stringify({name: className, section:'', active:true})}); classVal = created.id; } try{ await api(`/students?${schoolQuery()}`,{method:'POST', body: JSON.stringify({classId: Number(classVal), admissionNumber: $('#newAdmission').value, name: $('#newName').value, fatherName: $('#newGuardian').value, phone: $('#newPhone').value})}); $('#studentDialog').close(); e.target.reset(); await loadBase(); alert('Student added'); }catch(err){ alert(err.message); } });

// fee-row helper
function createFeeRow(){
  const row=document.createElement('div'); row.className='fee-row';
  // only allow TUITION and VAN in dropdown (if present); otherwise fall back to all types
  const allowed = (state.types||[]).filter(t => ['TUITION','VAN'].includes(String(t.code||'').toUpperCase()));
  const options = (allowed.length ? allowed : (state.types||[])).map(t=>`<option value="${t.id}">${esc(t.displayName)}</option>`).join('');
  const selectHTML = `<select class="fee-type"><option value="">Fee type</option>${options}</select>`;
  row.innerHTML = selectHTML + `<input class="fee-amount" type="number" min="0.01" step="0.01" placeholder="Amount">` + `<button type="button" class="remove" aria-label="Remove item">×</button>`;
  const sel = row.querySelector('select.fee-type');
  const amt = row.querySelector('input.fee-amount');
  const rem = row.querySelector('button.remove');
  sel.addEventListener('change', summary);
  amt.addEventListener('input', summary);
  rem.addEventListener('click', ()=>{ row.remove(); summary(); });
  return row;
}

function addFeeRow(){
  if(!state.types || !state.types.length){
    // No dynamic fee types allowed in this build — inform the user
    alert('No fee types available. Contact administrator.');
    return;
  }
  const row = createFeeRow(); $('#feeItems').append(row); amtFocus(row);
  summary();
}
function amtFocus(row){ const a = row.querySelector('input.fee-amount'); a && a.focus(); }

$('#addFeeItem')?.addEventListener('click', addFeeRow);


// receipt student population
$('#feeClass')?.addEventListener('change', ()=>{
  const cl = $('#feeClass').value;
  if(!cl){ $('#feeStudent').innerHTML = '<option value="">Choose a student</option>'; $('#feeStudent').disabled = true; return; }
  const opts = state.students.filter(s=>String(s.schoolClass?.id)===String(cl)).map(s=>`<option value="${s.id}">${esc(s.name)} (${esc(s.admissionNumber)})</option>`).join('');
  $('#feeStudent').innerHTML = '<option value="">Choose a student</option>' + (opts||'<option value="">No students in this class</option>');
  $('#feeStudent').disabled = false;
});

// fee form submit
$('#feeForm')?.addEventListener('submit', async e=>{
  e.preventDefault();
  const items = [...document.querySelectorAll('.fee-row')].map(r=>({ feeTypeId: Number(r.querySelector('select.fee-type').value), amount: Number(r.querySelector('input.fee-amount').value) })).filter(x=>x.feeTypeId && x.amount>0);
  if(!items.length) { alert('Add at least one fee item.'); return; }
  try{
    const receipt = await api(`/fees?${schoolQuery()}`, { method:'POST', body: JSON.stringify({ studentId: Number($('#feeStudent').value), paymentDate: $('#paymentDate').value, items, notes: (document.getElementById('paymentNotes')?.value||null) }) });
    // Notes are now sent to server and persisted. Prepare to print the receipt.
    $('#feeForm').reset(); $('#feeItems').innerHTML=''; if(state.types && state.types.length) addFeeRow(); summary();
    // prepend new receipt locally for instant UI feedback
    state.receipts = [receipt].concat(state.receipts || []);
    renderReceipts();
    // refresh in background to reconcile with server
    loadBase().catch(err=>console.warn('background refresh failed', err));
    alert(`Receipt ${receipt.receiptNumber} created successfully.`);
    // use printWindow helper
    printWindowForReceipt(receipt);
    // go to receipts view
    document.querySelectorAll('.nav-link[data-view]').forEach(b=>b.classList.toggle('active', b.dataset.view==='receipts')); document.querySelectorAll('.view').forEach(v=>v.classList.remove('active')); $('#receiptsView').classList.add('active');
  }catch(err){ alert(err.message); }
});

// helper: generate/print window for a receipt
function printWindowForReceipt(receipt){
  // populate class from students cache if missing
  let className = receipt.className || '';
  let section = receipt.section || '';
  if((!className || className==='') && receipt.studentId){
    const s = (state.students||[]).find(x=>Number(x.id)===Number(receipt.studentId));
    if(s && s.schoolClass){ className = s.schoolClass.name || ''; section = s.schoolClass.section || ''; }
  }
  const itemsHtml = (receipt.items||[]).map(i => `<tr><td>${esc(i.feeTypeName)}</td><td style="text-align:right">${money(i.amount)}</td></tr>`).join('');
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Receipt ${esc(receipt.receiptNumber)}</title><style>body{font-family:Arial,Helvetica,sans-serif;padding:20px}table{width:100%;border-collapse:collapse}td{padding:6px;border-bottom:1px solid #eee}</style></head><body><h2>Receipt ${esc(receipt.receiptNumber)}</h2><p><b>Student:</b> ${esc(receipt.studentName)} (${esc(receipt.admissionNumber)})<br><b>Class:</b> ${esc(className)} ${esc(section?(' - '+section):'')}</p><table>${itemsHtml}<tr><td style="text-align:right"><b>Total</b></td><td style="text-align:right"><b>${money(receipt.totalAmount)}</b></td></tr></table><p>${esc(receipt.notes||'')}</p><script>window.print();</script></body></html>`;
  try{
    const w = window.open('', '_blank', 'width=600,height=800');
    if(w && w.document){ w.document.open(); w.document.write(html); w.document.close(); }
    else {
      const w2 = window.open('about:blank', '_blank');
      if(w2 && w2.document){ w2.document.open(); w2.document.write(html); w2.document.close(); }
      else { alert('Could not open print window.'); }
    }
  }catch(e){ console.warn('print error', e); alert('Could not open print window.'); }
}

// student toggle flow: show confirm modal, then perform action
document.addEventListener('click', e => {
  const t = e.target;
  if (t.matches('.toggle-active')) {
    const id = t.dataset.id;
    const current = t.dataset.active === 'true';
    const newActive = !current;
    pendingToggleStudentId = id;
    pendingToggleNewState = newActive;
    const dlg = document.getElementById('confirmDialog');
    if (dlg) {
      document.getElementById('confirmMessage').textContent = `${newActive ? 'Activate' : 'Deactivate'} this student?`;
      dlg.showModal();
    } else {
      // fallback: perform immediately
      (async () => {
        try { await api(`/students/${id}?${schoolQuery()}`, { method: 'PATCH', body: JSON.stringify({ active: newActive }) }); await loadBase(); alert('Student status updated'); } catch (err) { alert(err.message); }
      })();
    }
  }
});

// confirm dialog handlers
document.addEventListener('click', async e => {
  const t = e.target;
  if (t.id === 'confirmYes') {
    const id = pendingToggleStudentId;
    const newActive = pendingToggleNewState;
    try {
      await api(`/students/${id}?${schoolQuery()}`, { method: 'PATCH', body: JSON.stringify({ active: newActive }) });
      const dlg = document.getElementById('confirmDialog'); dlg && dlg.close();
      pendingToggleStudentId = null; pendingToggleNewState = null;
      await loadBase();
      alert('Student status updated');
    } catch (err) { alert(err.message); }
  }
  if (t.id === 'confirmCancel') {
    const dlg = document.getElementById('confirmDialog'); dlg && dlg.close();
    pendingToggleStudentId = null; pendingToggleNewState = null;
  }
});

// initial
$('#paymentDate').value = new Date().toISOString().slice(0,10);
if(state.user) loadBase().catch(e=>console.error(e)); else $('#connectionDialog').showModal();
