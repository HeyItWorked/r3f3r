<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createReferral, getReferrals, updateReferralStatus } from './api/referrals.js'

const referrals = ref([])
const loading = ref(false)
const loadError = ref('')

const form = reactive({
    patientReference: '',
    specialistOffice: '',
    followUpDate: '',
})
const formErrors = ref({})
const saving = ref(false)
const saveError = ref('')

onMounted(loadReferrals)

async function loadReferrals() {
    loading.value = true
    loadError.value = ''
    try {
        const data = await getReferrals()
        for (const row of data) {
            row.editingStatus = row.status
        }
        referrals.value = data
    } catch (e) {
        console.log('load failed', e)
        loadError.value = 'Could not load referrals.'
    } finally {
        loading.value = false
    }
}

async function addReferral() {
    saving.value = true
    saveError.value = ''
    formErrors.value = {}
    try {
        await createReferral(form)
        form.patientReference = ''
        form.specialistOffice = ''
        form.followUpDate = ''
        await loadReferrals()
    } catch (e) {
        console.log('save failed', e)
        if (e.status === 400 && e.body && e.body.fieldErrors) {
            formErrors.value = e.body.fieldErrors
        } else {
            saveError.value = 'Could not save the referral.'
        }
    } finally {
        saving.value = false
    }
}

async function saveStatus(row) {
    const original = row.editingStatus
    try {
        const data = await updateReferralStatus(row.id, row.editingStatus)
        const updated = data.referral || data
        row.status = updated.status
        row.overdue = updated.overdue
        row.editingStatus = updated.status
    } catch (e) {
        console.log('status save failed', e)
        row.editingStatus = original // leave the displayed status unchanged
        row.rowError = 'Could not update this referral.'
        setTimeout(() => { row.rowError = '' }, 4000)
    }
}

function statusOptions(status) {
    return status === 'NEW' ? 'New' : status === 'SENT' ? 'Sent' : 'Done'
}
</script>

<template>
  <main class="scaffold">
    <p class="eyebrow">r3f3r</p>
    <h1>Referral tracker</h1>

    <div v-if="loadError" class="banner error">{{ loadError }}</div>

    <section class="card">
      <h2>Add a referral</h2>
      <form @submit.prevent="addReferral">
        <div class="field">
          <label>Patient reference</label>
          <input v-model="form.patientReference" type="text" placeholder="DEMO-101" />
          <span v-if="formErrors.patientReference" class="hint error">{{ formErrors.patientReference }}</span>
        </div>
        <div class="field">
          <label>Specialist office</label>
          <input v-model="form.specialistOffice" type="text" placeholder="Cardiology West" />
          <span v-if="formErrors.specialistOffice" class="hint error">{{ formErrors.specialistOffice }}</span>
        </div>
        <div class="field">
          <label>Follow-up date</label>
          <input v-model="form.followUpDate" type="date" />
          <span v-if="formErrors.followUpDate" class="hint error">{{ formErrors.followUpDate }}</span>
        </div>
        <div v-if="saveError" class="hint error">{{ saveError }}</div>
        <button type="submit" :disabled="saving">Add</button>
      </form>
    </section>

    <section class="card">
      <h2>Referrals</h2>
      <div v-if="loading" class="hint">Loading…</div>
      <p v-else-if="referrals.length === 0" class="empty">No referrals yet.</p>

      <table v-else class="referrals">
        <thead>
          <tr>
            <th>Patient reference</th>
            <th>Specialist office</th>
            <th>Follow-up</th>
            <th>Status</th>
            <th>Overdue</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in referrals" :key="row.id">
            <td>{{ row.patientReference }}</td>
            <td>{{ row.specialistOffice }}</td>
            <td>{{ row.followUpDate }}</td>
            <td>
              <select v-model="row.editingStatus">
                <option value="NEW">New</option>
                <option value="SENT">Sent</option>
                <option value="DONE">Done</option>
              </select>
            </td>
            <td>
              <span v-if="row.overdue" class="badge overdue">Overdue</span>
              <span v-else class="badge ok">Current</span>
            </td>
            <td class="actions">
              <button @click="saveStatus(row)" type="button">Save</button>
              <span v-if="row.rowError" class="hint error">{{ row.rowError }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <footer class="foot">r3f3r — referral tracker scaffold</footer>
  </main>
</template>
