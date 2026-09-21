<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  label: string
  content: string
}>()

const open = ref(false)

function show() {
  open.value = true
}

function hide() {
  open.value = false
}

function toggle() {
  open.value = !open.value
}
</script>

<template>
  <span
    class="insight-inline-help"
    @mouseenter="show"
    @mouseleave="hide"
  >
    <button
      type="button"
      class="insight-inline-help__trigger"
      :aria-label="`${props.label}说明`"
      :aria-expanded="open"
      @focus="show"
      @blur="hide"
      @click="toggle"
      @keydown.esc="hide"
    >
      ?
    </button>
    <span
      v-if="open"
      class="insight-inline-help__content"
      role="tooltip"
    >{{ props.content }}</span>
  </span>
</template>
