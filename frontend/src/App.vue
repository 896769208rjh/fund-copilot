<script setup>
import { computed, ref } from 'vue'

const draft = ref('')

const conversations = [
  { id: 'today', title: 'Fund service', meta: 'Active' },
  { id: 'rules', title: 'Trading rules', meta: 'Rules' },
  { id: 'risk', title: 'Risk disclosure', meta: 'Review' },
]

const messages = ref([
  {
    role: 'assistant',
    text: 'Hello, I am Fund Copilot. I can help with fund product details, trading rules, NAV information, and order-related service questions.',
    time: '09:30',
  },
  {
    role: 'user',
    text: 'How long does a mutual fund redemption usually take to confirm?',
    time: '09:31',
  },
  {
    role: 'assistant',
    text: 'Redemption confirmation depends on the fund type, trading calendar, and submission time. For most open-end funds, requests submitted before the trading cut-off are confirmed on the next trading day. The final result should follow the fund contract, sales platform rules, and official confirmation notice.',
    time: '09:31',
  },
])

const references = [
  'Fund contract',
  'Prospectus',
  'Trading rulebook',
]

const canSend = computed(() => draft.value.trim().length > 0)

function sendMessage() {
  const text = draft.value.trim()

  if (!text) {
    return
  }

  messages.value.push({
    role: 'user',
    text,
    time: 'Now',
  })

  messages.value.push({
    role: 'assistant',
    text: 'I have received your question. The next step is to connect this chat surface to the Spring AI Alibaba backend and stream a source-grounded response.',
    time: 'Now',
  })

  draft.value = ''
}
</script>

<template>
  <main class="workspace">
    <aside class="sidebar" aria-label="Conversations">
      <div class="brand">
        <div class="brand-mark">FC</div>
        <div>
          <p class="eyebrow">Fund Copilot</p>
          <h1>Service Desk</h1>
        </div>
      </div>

      <nav class="conversation-list">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-item"
          type="button"
        >
          <span>{{ conversation.title }}</span>
          <small>{{ conversation.meta }}</small>
        </button>
      </nav>
    </aside>

    <section class="chat-shell" aria-label="Chat">
      <header class="chat-header">
        <div>
          <p class="eyebrow">Mutual fund support</p>
          <h2>Customer conversation</h2>
        </div>
        <div class="status-pill">Compliant mode</div>
      </header>

      <div class="message-list">
        <article
          v-for="(message, index) in messages"
          :key="`${message.role}-${index}`"
          class="message"
          :class="`message-${message.role}`"
        >
          <p>{{ message.text }}</p>
          <time>{{ message.time }}</time>
        </article>
      </div>

      <form class="composer" @submit.prevent="sendMessage">
        <input
          v-model="draft"
          type="text"
          placeholder="Ask about a fund, NAV, order, or trading rule"
          aria-label="Message"
        />
        <button type="submit" :disabled="!canSend">Send</button>
      </form>
    </section>

    <aside class="insight-panel" aria-label="Compliance and references">
      <section>
        <p class="eyebrow">Risk boundary</p>
        <h2>Response guardrails</h2>
        <ul>
          <li>No yield promises</li>
          <li>No personalized investment advice</li>
          <li>Data date required for market values</li>
          <li>Identity check for asset queries</li>
        </ul>
      </section>

      <section>
        <p class="eyebrow">References</p>
        <h2>Source pool</h2>
        <div class="reference-list">
          <span v-for="reference in references" :key="reference">{{ reference }}</span>
        </div>
      </section>
    </aside>
  </main>
</template>
