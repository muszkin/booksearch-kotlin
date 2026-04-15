<script setup lang="ts">
interface Tab {
  key: string
  label: string
}

interface Props {
  tabs: Tab[]
  activeTab: string
}

defineProps<Props>()

const emit = defineEmits<{
  'update:activeTab': [key: string]
}>()

function handleKeydown(event: KeyboardEvent, index: number, tabs: Tab[]) {
  let targetIndex = index

  if (event.key === 'ArrowRight') {
    targetIndex = (index + 1) % tabs.length
  } else if (event.key === 'ArrowLeft') {
    targetIndex = (index - 1 + tabs.length) % tabs.length
  } else if (event.key === 'Home') {
    targetIndex = 0
  } else if (event.key === 'End') {
    targetIndex = tabs.length - 1
  } else {
    return
  }

  event.preventDefault()
  emit('update:activeTab', tabs[targetIndex].key)

  const tabList = (event.currentTarget as HTMLElement).parentElement
  const targetButton = tabList?.children[targetIndex] as HTMLElement | undefined
  targetButton?.focus()
}
</script>

<template>
  <div
    role="tablist"
    class="flex border-b border-zinc-700 bg-zinc-900"
  >
    <button
      v-for="(tab, index) in tabs"
      :key="tab.key"
      role="tab"
      :aria-selected="activeTab === tab.key"
      :tabindex="activeTab === tab.key ? 0 : -1"
      :class="[
        'min-h-[44px] px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
        activeTab === tab.key
          ? 'text-violet-400 border-violet-400'
          : 'text-zinc-400 border-transparent hover:text-zinc-100',
      ]"
      @click="emit('update:activeTab', tab.key)"
      @keydown="handleKeydown($event, index, tabs)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>
