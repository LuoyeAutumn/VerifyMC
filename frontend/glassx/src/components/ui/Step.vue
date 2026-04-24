<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <div class="py-4">
    <h2 v-if="title" class="mb-4 text-2xl font-bold tracking-tight text-foreground">
      {{ title }}
    </h2>
    <div class="text-muted-foreground leading-relaxed">
      <slot />
    </div>
  </div>
</template>
