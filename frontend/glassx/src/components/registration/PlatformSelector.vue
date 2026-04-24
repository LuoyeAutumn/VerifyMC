<template>
  <div v-if="bedrockEnabled" class="platform-selector">
    <Label class="mb-2">{{ label }}</Label>
    <div
      class="inline-flex w-full rounded-lg bg-white/5 border border-white/10 p-1 gap-1"
      role="radiogroup"
      :aria-label="label"
    >
      <Button
        type="button"
        variant="outline"
        class="flex-1 border-transparent focus:ring-offset-0"
        :class="modelValue === 'java' ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
        @click="selectPlatform('java')"
      >
        {{ javaLabel }}
      </Button>
      <Button
        type="button"
        variant="outline"
        class="flex-1 border-transparent focus:ring-offset-0"
        :class="modelValue === 'bedrock' ? 'bg-white/20 text-white shadow-sm hover:bg-white/20' : 'text-white/60 hover:bg-white/5 hover:text-white'"
        @click="selectPlatform('bedrock')"
      >
        {{ bedrockLabel }}
      </Button>
    </div>
    <p v-if="modelValue === 'bedrock'" class="mt-2 text-xs text-white/60">
      {{ prefixHint }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from '@/components/ui/Button.vue'
import Label from '@/components/ui/Label.vue'

export type Platform = 'java' | 'bedrock'

const props = withDefaults(defineProps<{
  modelValue: Platform
  bedrockEnabled?: boolean
  bedrockPrefix?: string
  username?: string
  label?: string
  javaLabel?: string
  bedrockLabel?: string
}>(), {
  bedrockEnabled: false,
  bedrockPrefix: '.',
  username: '',
  label: '',
  javaLabel: 'Java',
  bedrockLabel: 'Bedrock'
})

const emit = defineEmits<{
  'update:modelValue': [value: Platform]
  'update:username': [value: string]
  'platform-change': [value: Platform]
  'normalized-username': [value: string]
}>()

const { t } = useI18n()

const prefixHint = computed(() => {
  return t('register.form.platform_bedrock_prefix_hint', { prefix: props.bedrockPrefix })
})

const stripBedrockPrefixes = (username: string): string => {
  const prefix = props.bedrockPrefix
  if (!prefix) {
    return username
  }

  let rawUsername = username
  while (rawUsername.startsWith(prefix)) {
    rawUsername = rawUsername.slice(prefix.length)
  }
  return rawUsername
}

const normalizeUsername = (username: string, platform: Platform): string => {
  const trimmedUsername = username.trim()
  const rawUsername = props.bedrockEnabled ? stripBedrockPrefixes(trimmedUsername) : trimmedUsername
  
  if (!rawUsername) {
    return ''
  }

  if (platform === 'bedrock' && props.bedrockEnabled) {
    return `${props.bedrockPrefix}${rawUsername}`
  }

  return rawUsername
}

const getNormalizedUsername = (username: string, platform: Platform): string => {
  const rawUsername = stripBedrockPrefixes(username.trim())
  
  if (platform === 'bedrock' && props.bedrockEnabled && rawUsername) {
    return `${props.bedrockPrefix}${rawUsername}`
  }
  
  return rawUsername
}

const getUsernameForValidation = (username: string, platform: Platform): string => {
  const trimmedUsername = username.trim()
  
  if (!trimmedUsername) {
    return ''
  }

  if (platform === 'bedrock' && props.bedrockEnabled) {
    return stripBedrockPrefixes(trimmedUsername)
  }

  return trimmedUsername
}

const selectPlatform = (platform: Platform) => {
  emit('update:modelValue', platform)
  emit('platform-change', platform)
  
  if (props.username) {
    const normalized = normalizeUsername(props.username, platform)
    emit('update:username', normalized)
    emit('normalized-username', normalized)
  }
}

watch(() => props.username, (newUsername) => {
  if (props.modelValue === 'bedrock' && props.bedrockEnabled && newUsername?.trim()) {
    const normalized = normalizeUsername(newUsername, props.modelValue)
    if (normalized !== newUsername) {
      emit('update:username', normalized)
    }
  }
})

defineExpose({
  stripBedrockPrefixes,
  normalizeUsername,
  getNormalizedUsername,
  getUsernameForValidation
})
</script>

<style scoped>
.platform-selector {
  width: 100%;
}
</style>
