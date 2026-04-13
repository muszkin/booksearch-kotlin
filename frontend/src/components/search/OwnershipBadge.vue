<script setup lang="ts">
import FormatBadge from './FormatBadge.vue'

type MatchType = 'exact' | 'title' | 'author' | 'none'

interface Props {
  matchType: MatchType
  ownedFormats?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  ownedFormats: () => [],
})

const colorMap: Record<Exclude<MatchType, 'none'>, string> = {
  exact: 'text-emerald-300 bg-emerald-500/10',
  title: 'text-amber-300 bg-amber-500/10',
  author: 'text-sky-300 bg-sky-500/10',
}

const labelMap: Record<Exclude<MatchType, 'none'>, string> = {
  exact: 'OWNED',
  title: 'SAME TITLE',
  author: 'SAME AUTHOR',
}
</script>

<template>
  <div v-if="props.matchType !== 'none'" class="flex items-center gap-2 flex-wrap">
    <span
      :class="[
        'text-xs px-2 py-0.5 rounded-full font-medium',
        colorMap[props.matchType as Exclude<MatchType, 'none'>],
      ]"
    >
      {{ labelMap[props.matchType as Exclude<MatchType, 'none'>] }}
    </span>
    <template v-if="props.matchType === 'exact' && props.ownedFormats.length > 0">
      <FormatBadge
        v-for="fmt in props.ownedFormats"
        :key="fmt"
        :format="fmt"
      />
    </template>
  </div>
</template>
