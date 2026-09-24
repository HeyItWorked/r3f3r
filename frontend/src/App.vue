<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { getReferrals, createReferral, updateReferralStatus, getHistory, rescheduleReferral, getContacts, addContact, getProviders, addProvider, renameProvider } from './api/referrals.js'
import * as helpers from './helpers.js'
// SAP R/3 - Medical Referral Tracking [MR100]
// Functional integration with the Spring Boot /api/referrals API.
const DUE_LABELS = { ALL: 'All dates', OVERDUE: 'Overdue', TODAY: 'Due today', NEXT_7_DAYS: 'Next 7 days' }
const SORT_LABELS = { patientRef: 'Patient Ref', office: 'Specialist Office', followUp: 'Follow-up Date', status: 'Status' }
const CHANNELS = { PHONE: 'Phone', EMAIL: 'Email' }
const ACTIONS = { HISTORY: 'History', RESCHEDULE: 'Reschedule', CONTACT: 'Log contact' }
const OUTCOMES = { NO_RESPONSE: 'No response', CALLBACK_REQUESTED: 'Callback requested', APPOINTMENT_CONFIRMED: 'Appointment confirmed' }
const referrals = ref([])
const selectedId = ref(null)
const draftStatus = ref('')          // status being edited for the selected row
const loading = ref(false)
const saving = ref(false)            // a status PATCH is in flight
const connected = ref(true)          // last backend call succeeded
const loadError = ref('')
const saveError = ref('')
const notice = ref('')
// one object per dialog
const create = ref({ open: false })
const hist = ref({ open: false })
const resched = ref({ open: false })
const contact = ref({ open: false })
const prov = ref({ open: false })
const dialogs = [create, hist, resched, contact, prov]
const anyDialog = computed(() => dialogs.some(d => d.value.open))
const dialogRow = ref(null)          // copy of the row the dialog was opened for
const actionPick = ref('')
const providers = ref([])
const providersError = ref('')
const overdueCount = computed(() => referrals.value.filter(r => r.overdue).length)

// ---- prefs (localStorage) ----
function loadPrefs() {
  var p2 = { findText: '', statusFilter: '', dueView: 'ALL', sortKey: null, sortDir: 'asc' }
  try {
    var p = JSON.parse(localStorage.getItem('r3f3r.tablePreferences.v1'))
    if (p && p.version === 1) {
      if (typeof p.findText == 'string' && p.findText.length <= 200) p2.findText = p.findText
      if (['', 'NEW', 'SENT', 'DONE'].includes(p.statusFilter)) p2.statusFilter = p.statusFilter
      if (p.dueView in DUE_LABELS) p2.dueView = p.dueView
      if (p.sortKey === null || p.sortKey in SORT_LABELS) p2.sortKey = p.sortKey
      if (['asc', 'desc'].includes(p.sortDir)) p2.sortDir = p.sortDir
    }
  } catch (e) {
    console.log('could not read prefs', e)
  }
  return p2
}
const prefs = loadPrefs()

// ---- Client-side table exploration (no new API calls) ----
const findText = ref(prefs.findText)             // matches Patient Ref or Specialist Office
const dueView = ref(prefs.dueView)
const statusFilter = ref(prefs.statusFilter)
const sortKey = ref(prefs.sortKey)            // null = the backend's newest-first order
const sortDir = ref(prefs.sortDir)
watch([findText, statusFilter, dueView, sortKey, sortDir], () => {
  try {
    localStorage.setItem('r3f3r.tablePreferences.v1', JSON.stringify({
      version: 1, findText: findText.value.length <= 200 ? findText.value : '', statusFilter: statusFilter.value,
      dueView: dueView.value, sortKey: sortKey.value, sortDir: sortDir.value,
    }))
  } catch (e) {}
})
function resetPrefs() {
  findText.value = ''; statusFilter.value = ''; dueView.value = 'ALL'; sortKey.value = null; sortDir.value = 'asc'
}
const STATUS_RANK = { NEW: 0, SENT: 1, DONE: 2 }
const isFiltered = computed(() => findText.value.trim() !== '' || dueView.value !== 'ALL' || statusFilter.value !== '')
const visible = computed(() => {
  var q = findText.value.trim().toLowerCase()
  var list = referrals.value.filter(r => {
    if (dueView.value === 'OVERDUE' && !r.overdue) return false
    if (dueView.value === 'TODAY') {
      if (r.status == 'DONE' || r.daysUntil != 0) return false
    }
    if (dueView.value === 'NEXT_7_DAYS') {
      if (r.status == 'DONE' || r.daysUntil < 1 || r.daysUntil > 7) return false
    }
    if (statusFilter.value && r.status !== statusFilter.value) return false
    if (!q) return true
    return r.patientRef.toLowerCase().includes(q) || r.office.toLowerCase().includes(q)
  })
  if (!sortKey.value) return list
  var dir = sortDir.value == 'asc' ? 1 : -1
  var keyFn = {
    patientRef: r => r.patientRef.toLowerCase(),
    office: r => r.office.toLowerCase(),
    followUp: r => r.followUpRaw,      // ISO sorts correctly as a string
    status: r => STATUS_RANK[r.status] ?? 99,
  }[sortKey.value]
  return [...list].sort((a, b) => {
    var x = keyFn(a), y = keyFn(b)
    return x < y ? -dir : x > y ? dir : 0
  })
})
function toggleSort(key) {
  if (sortKey.value == key) {
    if (sortDir.value == 'asc') sortDir.value = 'desc'
    else sortDir.value = 'asc'
  } else { sortKey.value = key; sortDir.value = 'asc' }
}
function ariaSort(key) {
  if (sortKey.value !== key) return 'none'
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}
// If a filter change hides the selected row, protect an unsaved status edit
// before the selection is cleared; on cancel, undo the filter change instead.
let prev = [prefs.findText, prefs.dueView, prefs.statusFilter]
watch([findText, dueView, statusFilter], (_current, old) => { prev = old })
// no idea why this needs two watchers but it breaks if you remove one, DONT TOUCH
watch(visible, (list) => {
  if (!selectedId.value || list.some(r => r.id === selectedId.value)) return
  if (confirmDiscardIfDirty(null)) {
    selectedId.value = null
  } else {
    [findText.value, dueView.value, statusFilter.value] = prev
  }
})
// The selected row has an edited status that has not been saved yet.
const isDirty = computed(() => {
  const row = referrals.value.find(r => r.id === selectedId.value)
  return !!row && draftStatus.value !== row.status
})
// 1-based position of the selected row in the visible list ('—' when none).
const selectedPos = computed(() => {
  const idx = visible.value.findIndex(r => r.id === selectedId.value)
  return idx >= 0 ? idx + 1 : '—'
})
// Never lose an edited status silently: ask before discarding it.
function confirmDiscardIfDirty(targetId) {
  if (targetId === selectedId.value || !isDirty.value) return true
  return window.confirm('Discard the unsaved status change?')
}
async function loadReferrals() {
  if (loading.value) return          // no repeat submissions while pending
  if (!confirmDiscardIfDirty(null)) return
  loading.value = true
  loadError.value = ''; saveError.value = ''; notice.value = ''
  try {
    var data = await getReferrals()
    console.log('loaded', data)
    referrals.value = (data || []).map(helpers.normalize)
    connected.value = true
    if (referrals.value.length) selectRow(referrals.value[0].id)
    else selectedId.value = null
  } catch (e) {
    // Keep the previously loaded rows; they are real, just possibly stale.
    connected.value = false
    loadError.value = helpers.errMsg(e, 'Could not load referrals')
  } finally {
    loading.value = false
  }
}
function selectRow(id) {
  if (!confirmDiscardIfDirty(id)) return
  selectedId.value = id
  var row = referrals.value.find(r => r.id == id)
  draftStatus.value = row ? row.status : ''
}
// Toolbar Save: commit the selected row's draft status.
// On failure, restore the previously saved value and show an error.
async function saveStatus() {
  const row = referrals.value.find(r => r.id === selectedId.value)
  if (!row || saving.value) return
  saving.value = true
  saveError.value = ''; notice.value = ''
  try {
    const res = await updateReferralStatus(row.id, draftStatus.value)
    connected.value = true
    Object.assign(row, helpers.normalize(res.referral))
    notice.value = 'Status saved'
  } catch (e) {
    connected.value = false
    if (selectedId.value === row.id) draftStatus.value = row.status
    saveError.value = helpers.errMsg(e, 'Could not update referral')
  } finally {
    saving.value = false
  }
}

// ---- create ----
function openCreate() {
  create.value = { open: true, busy: false, error: '', fieldErrors: {}, patientReference: '', specialistOffice: '', followUpDate: '', officeChoice: '' }
  loadProviders()
}
// On failure, keep the typed values and show the server/field errors.
async function createReferralRecord() {
  var d = create.value
  if (d.busy) return        // no repeat submissions while pending
  d.busy = true; d.error = ''; d.fieldErrors = {}
  var payload = { patientReference: d.patientReference, followUpDate: d.followUpDate }
  if (d.officeChoice == 'manual' || providers.value.length == 0) payload.specialistOffice = d.specialistOffice
  else if (d.officeChoice !== '') payload.providerId = Number(d.officeChoice)
  try {
    const res = await createReferral(payload)
    connected.value = true
    referrals.value.unshift(helpers.normalize(res.referral))
    selectRow(res.referral.id)
    d.open = false
  } catch (e) {
    d.fieldErrors = (e.body && e.body.fieldErrors) || {}
    d.error = helpers.errMsg(e, 'Could not create referral')
  }
  d.busy = false
}

// ---- row actions (History / Reschedule / Log contact) ----
async function runAction() {
  var row = referrals.value.find(r => r.id === selectedId.value)
  var a = actionPick.value
  if (!row) return
  if (a != 'HISTORY' && isDirty.value) {
    if (!window.confirm('Discard the unsaved status change?')) return
    draftStatus.value = row.status
  }
  dialogRow.value = { ...row }
  if (a == 'HISTORY') {
    hist.value = { open: true, loading: true, error: '', rows: [] }
    try { hist.value.rows = await getHistory(row.id) } catch (e) { hist.value.error = helpers.errMsg(e, 'Could not load history') }
    hist.value.loading = false
  } else if (a == 'RESCHEDULE') {
    resched.value = { open: true, busy: false, error: '', date: row.followUpRaw }
  } else if (a == 'CONTACT') {
    contact.value = { open: true, busy: false, error: '', fieldErrors: {}, loadError: '', rows: [], channel: '', outcome: '' }
    try { contact.value.rows = await getContacts(row.id) } catch (e) { contact.value.loadError = helpers.errMsg(e, 'Could not load contact attempts') }
  }
}
async function saveReschedule() {
  var d = resched.value
  if (d.busy) return
  d.busy = true; d.error = ''
  var id = dialogRow.value.id
  try {
    var res = await rescheduleReferral(id, d.date)
    connected.value = true
    var row = referrals.value.find(r => r.id === id)
    if (row) Object.assign(row, helpers.normalize(res.referral))
    d.open = false
    notice.value = 'Follow-up date updated'
  } catch (e) {
    d.error = helpers.errMsg(e, 'Could not reschedule', 'followUpDate')
  }
  d.busy = false
}
async function saveContact() {
  var d = contact.value
  if (d.busy) return
  d.busy = true; d.error = ''; d.fieldErrors = {}
  var id = dialogRow.value.id
  try {
    await addContact(id, d.channel, d.outcome)
    d.channel = ''; d.outcome = ''
    try { d.rows = await getContacts(id) } catch (e) { d.loadError = 'Recorded, but the list could not be refreshed' }
  } catch (e) {
    d.fieldErrors = (e.body && e.body.fieldErrors) || {}
    d.error = helpers.errMsg(e, 'Could not record attempt')
  }
  d.busy = false
}

// ---- providers ----
async function loadProviders() {
  try {
    providers.value = await getProviders()
    providersError.value = ''
  } catch (e) {
    providersError.value = 'Provider directory unavailable: ' + helpers.errMsg(e, '')
  }
}
async function openProviders() {
  prov.value = { open: true, busy: false, error: '', name: '', editingId: null, editName: '' }
  loadProviders()
}
async function saveProvider(editing) {
  var d = prov.value
  if (d.busy) return
  d.busy = true; d.error = ''
  try {
    if (editing) {
      await renameProvider(d.editingId, d.editName)
      d.editingId = null
    } else {
      await addProvider(d.name)
      d.name = ''
    }
    await loadProviders()
  } catch (e) {
    d.error = helpers.errMsg(e, 'Could not save provider', 'name')
  }
  d.busy = false
}

// ---- print ----
const printedAt = ref('')
const printBlocked = computed(() => {
  if (loading.value) return 'Print unavailable while loading'
  if (loadError.value) return 'Print unavailable: list failed to load'
  if (anyDialog.value) return 'Print unavailable while a dialog is open'
  if (saving.value) return 'Print unavailable while saving'
  if (isDirty.value) return 'Save or discard the status change to print'
  return ''
})
const filterDesc = computed(() => [findText.value.trim() && 'Find "' + findText.value.trim() + '"',
  statusFilter.value && 'Status ' + statusFilter.value, 'Due view: ' + DUE_LABELS[dueView.value]].filter(Boolean).join(' · '))
const sortDesc = computed(() => sortKey.value ? SORT_LABELS[sortKey.value] + (sortDir.value === 'asc' ? ' ascending' : ' descending') : 'Newest first (default)')
function doPrint() {
  if (printBlocked.value) return
  printedAt.value = new Date().toLocaleString() + ' (' + Intl.DateTimeFormat().resolvedOptions().timeZone + ')'
  setTimeout(() => window.print(), 50)
}
const toolbar = [
  { label: 'Create',  icon: '/icons/se98/document-new.png',  action: openCreate },
  { label: 'Save',    icon: '/icons/se98/document-save.png', action: saveStatus },
  { label: 'Refresh', icon: '/icons/se98/view-refresh.png',  action: loadReferrals },
]
// Truthful disabled states: Save needs a selected row with an unsaved edit and
// no pending request; Refresh is blocked while a load is in flight.
function isDisabled(t) {
  if (t.label == 'Save') return !selectedId.value || !isDirty.value || saving.value
  if (t.label == 'Refresh') return loading.value
  // if (t.label == 'Delete') return true
  return false
}
function onKeydown(e) {
  if (e.key === 'Escape') {
    dialogs.forEach(d => { if (d.value.open && !d.value.busy) d.value.open = false })
    return
  }
  if (anyDialog.value) return
  var a = document.activeElement
  if (a && (a.tagName == 'BUTTON' || a.tagName == 'SELECT' || a.tagName == 'INPUT')) return
  var list = visible.value
  if (list.length == 0) return
  var current = selectedId.value ? list.findIndex(r => r.id == selectedId.value) : 0
  var next  // TODO wrap around is weird on long lists
  if (e.key === 'ArrowDown') next = (current + 1) % list.length
  else if (e.key === 'ArrowUp') next = (current - 1 + list.length) % list.length
  else return
  e.preventDefault()
  selectRow(list[next].id)
}
onMounted(loadReferrals)
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="sap-app">
    <header class="title-bar">
      <h1 class="title-text">Medical Referral Tracking</h1>
      <span class="module-code">MR100</span>
    </header>

    <div class="toolbar">
      <button v-for="t in toolbar" :key="t.label" type="button" class="tool-btn" :disabled="isDisabled(t)" @click="t.action()">
        <img class="icon" :src="t.icon" alt="" />
        <span class="tool-label">{{ t.label }}</span>
      </button>
      <span class="toolbar-hint" :class="{ unsaved: isDirty }">{{ isDirty ? 'Status changed — save to apply' : 'Select a referral to update its status' }}</span>
    </div>

    <div class="section-head">
      <h2>Referrals</h2>
      <span class="section-caption">Specialist follow-up register</span>
    </div>
    <div class="grid-tools">
      <label class="grid-find">Find: <input class="find-input" v-model="findText" type="text" placeholder="Patient ref or office" /></label>
      <label class="grid-status">Status:
        <select v-model="statusFilter" class="filter-select">
          <option value="">All statuses</option><option value="NEW">New</option><option value="SENT">Sent</option><option value="DONE">Done</option>
        </select>
      </label>
      <label class="grid-status">Due:
        <select v-model="dueView" class="filter-select">
          <option v-for="(label, v) in DUE_LABELS" :key="v" :value="v">{{ label }}</option>
        </select>
      </label>
      <button v-if="isFiltered" type="button" class="clear-filter" @click="findText = ''; dueView = 'ALL'; statusFilter = ''">Clear filters</button>
    </div>
    <div class="grid-tools">
      <label class="grid-status">Actions:
        <select v-model="actionPick" class="filter-select" :disabled="!selectedId">
          <option value="">Choose…</option><option v-for="(l, v) in ACTIONS" :key="v" :value="v">{{ l }}</option>
        </select>
        <button type="button" class="clear-filter" :disabled="!selectedId || !actionPick" @click="runAction()">Go</button>
      </label>
      <span class="global-tools">
        <button type="button" class="clear-filter" @click="openProviders()">Providers</button>
        <button type="button" class="clear-filter" :disabled="!!printBlocked" @click="doPrint()">Print</button>
        <button type="button" class="clear-filter" @click="resetPrefs()">Reset table preferences</button>
        <span v-if="printBlocked" class="print-hint">{{ printBlocked }}</span>
      </span>
    </div>

    <div v-if="loadError" class="banner error" role="alert">⚠ {{ loadError }}</div>
    <div v-if="saveError" class="banner error" role="alert">⚠ {{ saveError }}</div>
    <div role="status"><div v-if="notice" class="banner ok">{{ notice }}</div></div>

    <div class="grid-wrap" tabindex="0">
      <table class="data-grid">
        <colgroup><col v-for="n in 5" :key="n" /></colgroup>
        <thead>
          <tr>
            <th v-for="(label, k) in SORT_LABELS" :key="k" :aria-sort="ariaSort(k)"><button type="button" class="th-sort" @click="toggleSort(k)">{{ label }} <span class="sort-dir" v-if="sortKey === k">{{ sortDir === 'asc' ? '▲' : '▼' }}</span></button></th>
            <th>Attention</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td class="state-cell" colspan="5">Loading referrals…</td></tr>
          <tr v-else-if="!loadError && referrals.length === 0"><td class="state-cell" colspan="5">No referrals yet. Use Create to add the first referral.</td></tr>
          <tr v-else-if="visible.length === 0"><td class="state-cell" colspan="5">No referrals match the current filter.</td></tr>
          <tr v-else v-for="r in visible" :key="r.id" :class="{ sel: selectedId === r.id }" tabindex="0"
              @keydown.enter.self.prevent="selectRow(r.id)" @keydown.space.self.prevent="selectRow(r.id)" @click="selectRow(r.id)">
            <td class="patient-ref">{{ r.patientRef }}</td>
            <td>{{ r.office }}</td>
            <td :class="{ 'overdue-date': r.overdue }">{{ r.followUp }}</td>
            <td>
              <select v-if="selectedId === r.id" v-model="draftStatus" class="status-select" aria-label="Referral status" @keydown.enter.prevent="saveStatus()">
                <option v-for="st in ['NEW', 'SENT', 'DONE']" :key="st">{{ st }}</option>
              </select>
              <span v-else>{{ r.status }}</span>
            </td>
            <td class="overdue-cell"><span v-if="r.daysOverdue > 0" class="flag"><span aria-hidden="true">!</span> {{ helpers.attentionText(r) }}</span><span v-else class="no-attention">—</span></td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer class="status-bar">
      <span class="status-left"><template v-if="isFiltered">{{ visible.length }} of {{ referrals.length }} referrals</template><template v-else>{{ referrals.length }} referrals listed</template> | {{ overdueCount }} overdue total<template v-if="isDirty"> | Unsaved changes</template></span>
      <span class="status-right">
        <span v-if="loading">Loading…</span>
        <span v-else-if="!connected" class="state-offline">Backend unreachable</span>
        <span v-else>Connected</span>
        &nbsp;<span class="indicator">{{ selectedPos }} / {{ visible.length }}</span>
      </span>
    </footer>
    <p class="demo-notice">DEMO ENVIRONMENT · Fictional data only. No authentication or permissions; not for real patient records. Table preferences, including the Find text, are saved in this browser.</p>

    <!-- Create Referral dialog -->
    <div v-if="create.open" class="dialog-backdrop" @click.self="!create.busy && (create.open = false)">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="Create Referral">
        <div class="dialog-title">Create Referral [F-01]</div>
        <div class="dialog-body">
          <div v-if="create.error" class="banner error" role="alert">⚠ {{ create.error }}</div>
          <label>Patient Ref
            <input v-model="create.patientReference" placeholder="e.g. MRN012345" />
            <span v-if="create.fieldErrors.patientReference" class="field-error">{{ create.fieldErrors.patientReference }}</span>
          </label>
          <span v-if="providersError" class="field-error">{{ providersError }}</span>
          <label v-if="providers.length">Specialist Office
            <select v-model="create.officeChoice">
              <option value="">Choose provider</option>
              <option v-for="p in providers" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
              <option value="manual">Enter office manually</option>
            </select>
          </label>
          <label v-if="!providers.length || create.officeChoice == 'manual'">{{ providers.length ? 'Office (manual)' : 'Specialist Office' }}
            <input v-model="create.specialistOffice" placeholder="Clinic - Dr. Name" />
          </label>
          <span v-if="create.fieldErrors.specialistOffice" class="field-error">{{ create.fieldErrors.specialistOffice }}</span>
          <label>Follow-up Date
            <input type="date" v-model="create.followUpDate" />
            <span v-if="create.fieldErrors.followUpDate" class="field-error">{{ create.fieldErrors.followUpDate }}</span>
          </label>
          <div class="dialog-actions"><button type="button" class="dlg-btn" :disabled="create.busy" @click="createReferralRecord()">Save</button> <button type="button" class="dlg-btn" :disabled="create.busy" @click="create.open = false">Cancel</button></div>
        </div>
      </div>
    </div>

    <!-- History dialog -->
    <div v-if="hist.open" class="dialog-backdrop" @click.self="hist.open = false">
      <div class="dialog dialog-wide" role="dialog" aria-modal="true" aria-label="Status History">
        <div class="dialog-title">Status History — {{ dialogRow.patientRef }}</div>
        <div class="dialog-body">
          <div v-if="hist.error" class="banner error" role="alert">⚠ {{ hist.error }}</div>
          <p class="dlg-note">History begins when this feature was enabled.</p>
          <p v-if="hist.loading">Loading…</p>
          <p v-else-if="!hist.error && !hist.rows.length">No status changes recorded.</p>
          <table v-else-if="hist.rows.length" class="mini-grid">
            <thead><tr><th>Previous status</th><th>New status</th><th>Changed at (local time)</th></tr></thead>
            <tbody><tr v-for="h in hist.rows" :key="h.id"><td>{{ h.fromStatus }}</td><td>{{ h.toStatus }}</td><td>{{ helpers.fmtTime(h.changedAt) }}</td></tr></tbody>
          </table>
          <div class="dialog-actions"><button type="button" class="dlg-btn" @click="hist.open = false">Close</button></div>
        </div>
      </div>
    </div>

    <!-- Reschedule dialog -->
    <div v-if="resched.open" class="dialog-backdrop" @click.self="!resched.busy && (resched.open = false)">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="Reschedule Follow-up">
        <div class="dialog-title">Reschedule Follow-up</div>
        <div class="dialog-body">
          <div v-if="resched.error" class="banner error" role="alert">⚠ {{ resched.error }}</div>
          <p class="dlg-note"><b>{{ dialogRow.patientRef }}</b> · {{ dialogRow.office }}<br />Current date: {{ dialogRow.followUp }}</p>
          <label>New follow-up date <input type="date" v-model="resched.date" /></label>
          <div class="dialog-actions"><button type="button" class="dlg-btn" :disabled="resched.busy" @click="saveReschedule()">Save</button> <button type="button" class="dlg-btn" :disabled="resched.busy" @click="resched.open = false">Cancel</button></div>
        </div>
      </div>
    </div>

    <!-- Contact dialog -->
    <div v-if="contact.open" class="dialog-backdrop" @click.self="!contact.busy && (contact.open = false)">
      <div class="dialog dialog-wide" role="dialog" aria-modal="true" aria-label="Log Contact Attempt">
        <div class="dialog-title">Log Contact Attempt — {{ dialogRow.patientRef }}</div>
        <div class="dialog-body">
          <div v-if="contact.error" class="banner error" role="alert">⚠ {{ contact.error }}</div>
          <p class="dlg-note">Records an attempt only; does not send a message.</p>
          <label>Channel
            <select v-model="contact.channel"><option value="">Choose…</option><option v-for="(l, v) in CHANNELS" :key="v" :value="v">{{ l }}</option></select>
            <span v-if="contact.fieldErrors.channel" class="field-error">{{ contact.fieldErrors.channel }}</span>
          </label>
          <label>Outcome
            <select v-model="contact.outcome"><option value="">Choose…</option><option v-for="(l, v) in OUTCOMES" :key="v" :value="v">{{ l }}</option></select>
            <span v-if="contact.fieldErrors.outcome" class="field-error">{{ contact.fieldErrors.outcome }}</span>
          </label>
          <div class="dialog-actions"><button type="button" class="dlg-btn" :disabled="contact.busy" @click="saveContact()">Record</button> <button type="button" class="dlg-btn" :disabled="contact.busy" @click="contact.open = false">Close</button></div>
          <div v-if="contact.loadError" class="banner error" role="alert">⚠ {{ contact.loadError }}</div>
          <p v-if="!contact.rows.length && !contact.loadError">No contact attempts recorded.</p>
          <table v-else-if="contact.rows.length" class="mini-grid">
            <thead><tr><th>Channel</th><th>Outcome</th><th>Recorded at (local time)</th></tr></thead>
            <tbody><tr v-for="c in contact.rows" :key="c.id"><td>{{ CHANNELS[c.channel] }}</td><td>{{ OUTCOMES[c.outcome] }}</td><td>{{ helpers.fmtTime(c.recordedAt) }}</td></tr></tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Providers dialog -->
    <div v-if="prov.open" class="dialog-backdrop" @click.self="!prov.busy && (prov.open = false)">
      <div class="dialog dialog-wide" role="dialog" aria-modal="true" aria-label="Provider Directory">
        <div class="dialog-title">Provider Directory</div>
        <div class="dialog-body">
          <div v-if="providersError" class="banner error" role="alert">⚠ {{ providersError }}</div>
          <div v-if="prov.error" class="banner error" role="alert">⚠ {{ prov.error }}</div>
          <p class="dlg-note">Renaming a provider only affects future referrals. Existing referrals keep the office name they were created with.</p>
          <table v-if="providers.length" class="mini-grid">
            <tbody>
              <tr v-for="p in providers" :key="p.id">
                <td v-if="prov.editingId === p.id"><input v-model="prov.editName" aria-label="Provider name" /></td>
                <td v-else>{{ p.name }}</td>
                <td class="mini-actions" v-if="prov.editingId === p.id">
                  <button type="button" class="dlg-btn" :disabled="prov.busy" @click="saveProvider(true)">Save</button>
                  <button type="button" class="dlg-btn" :disabled="prov.busy" @click="prov.editingId = null">Cancel</button>
                </td>
                <td class="mini-actions" v-else><button type="button" class="dlg-btn" :disabled="prov.busy" @click="prov.editingId = p.id; prov.editName = p.name">Edit</button></td>
              </tr>
            </tbody>
          </table>
          <p v-else-if="!providersError">No providers yet.</p>
          <label>New provider <input v-model="prov.name" placeholder="Clinic - Dr. Name" /></label>
          <div class="dialog-actions"><button type="button" class="dlg-btn" :disabled="prov.busy" @click="saveProvider(false)">Add</button> <button type="button" class="dlg-btn" :disabled="prov.busy" @click="prov.open = false">Close</button></div>
        </div>
      </div>
    </div>
  </div>

  <!-- only shows up when printing -->
  <div class="print-area">
    <h1>Medical Referral Tracking — Worklist</h1>
    <p>Generated {{ printedAt }}</p>
    <p>Filters: {{ filterDesc }} · Sort: {{ sortDesc }} · {{ visible.length }} of {{ referrals.length }} referrals shown</p>
    <p>DEMO ENVIRONMENT · Fictional data only. Not for real patient records.</p>
    <p v-if="visible.length === 0">No referrals match the current filter.</p>
    <table v-else>
      <thead><tr><th>Patient Ref</th><th>Specialist Office</th><th>Follow-up Date</th><th>Status</th><th>Attention</th></tr></thead>
      <tbody>
        <tr v-for="r in visible" :key="r.id"><td>{{ r.patientRef }}</td><td>{{ r.office }}</td><td>{{ r.followUp }}</td><td>{{ r.status }}</td><td>{{ r.daysOverdue > 0 ? '! ' + helpers.attentionText(r) : '—' }}</td></tr>
      </tbody>
    </table>
  </div>
</template>
