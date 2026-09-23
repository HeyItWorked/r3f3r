<script setup>
import { onMounted, ref } from 'vue'

const HEALTH_TIMEOUT_MS = 5000
const connectionMessage = ref('Checking backend connection…')

onMounted(async () => {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS)

  try {
    const response = await fetch('/api/health', { signal: controller.signal })
    if (!response.ok) {
      throw new Error(`Health check returned ${response.status}`)
    }

    const body = await response.json()
    connectionMessage.value = body.status === 'UP' ? 'Backend connected' : 'Backend returned an unexpected status'
  } catch {
    connectionMessage.value = 'Backend unavailable'
  } finally {
    window.clearTimeout(timeout)
  }
})
</script>

<template>
  <main class="scaffold">
    <p class="eyebrow">r3f3r</p>
    <h1>Referral tracker</h1>
    <p class="lede">Vue and Spring Boot are ready for the referral workflow.</p>
    <p class="connection" role="status">{{ connectionMessage }}</p>
  </main>
</template>
