<template>
  <div v-if="hasAnyMethod" class="verification-progress rounded-lg border border-white/10 bg-white/5 p-4 space-y-3">
    <div class="flex items-center justify-between">
      <h4 class="text-sm font-medium text-white/80">{{ $t('auth.verificationProgress') }}</h4>
      <span class="text-xs text-white/50">{{ completedCount }}/{{ totalRequired }} {{ $t('auth.required') }}</span>
    </div>

    <div class="h-1.5 bg-white/10 rounded-full overflow-hidden">
      <div
        class="h-full bg-gradient-to-r from-blue-500 to-cyan-400 transition-all duration-300"
        :style="{ width: `${progressPercentage}%` }"
      ></div>
    </div>

    <div class="flex flex-wrap gap-2">
      <div
        v-for="method in allMethods"
        :key="method.type"
        class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-xs transition-all"
        :class="getMethodClass(method)"
      >
        <span v-if="method.completed" class="text-green-400">✓</span>
        <span v-else class="text-white/40">○</span>
        <span>{{ getMethodLabel(method.type) }}</span>
        <span
          v-if="method.required"
          class="px-1 py-0.5 rounded text-[10px]"
          :class="method.completed ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'"
        >
          {{ $t('auth.required') }}
        </span>
        <span
          v-else
          class="px-1 py-0.5 rounded text-[10px]"
          :class="method.completed ? 'bg-blue-500/20 text-blue-300' : 'bg-white/10 text-white/50'"
        >
          {{ $t('auth.optional') }}
        </span>
      </div>
    </div>

    <p v-if="showMinOptionalHint" class="text-xs text-amber-300/80">
      {{ $t('auth.minOptionalRequired', { count: minOptionalRequired }) }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AuthMethodType } from '@/composables/useAuthMethods'

interface MethodItem {
  type: AuthMethodType
  required: boolean
  completed: boolean
}

interface Props {
  mustMethods: AuthMethodType[]
  optionalMethods: AuthMethodType[]
  minOptionalRequired: number
  completedMethods: AuthMethodType[]
  isMethodEnabled: (type: AuthMethodType) => boolean
}

const props = defineProps<Props>()

const { t } = useI18n()

const allMethods = computed<MethodItem[]>(() => {
  const methods: MethodItem[] = []

  for (const type of props.mustMethods) {
    if (props.isMethodEnabled(type)) {
      methods.push({
        type,
        required: true,
        completed: props.completedMethods.includes(type)
      })
    }
  }

  for (const type of props.optionalMethods) {
    if (props.isMethodEnabled(type)) {
      methods.push({
        type,
        required: false,
        completed: props.completedMethods.includes(type)
      })
    }
  }

  return methods
})

const hasAnyMethod = computed(() => allMethods.value.length > 0)

const completedCount = computed(() => {
  let count = 0
  for (const method of allMethods.value) {
    if (method.required && method.completed) {
      count++
    }
  }

  const completedOptional = allMethods.value.filter(m => !m.required && m.completed).length
  const neededOptional = Math.min(completedOptional, props.minOptionalRequired)
  count += neededOptional

  return count
})

const totalRequired = computed(() => {
  const requiredCount = allMethods.value.filter(m => m.required).length
  return requiredCount + props.minOptionalRequired
})

const progressPercentage = computed(() => {
  if (totalRequired.value === 0) return 100
  return Math.min(100, Math.round((completedCount.value / totalRequired.value) * 100))
})

const showMinOptionalHint = computed(() => {
  if (props.minOptionalRequired <= 0) return false
  const completedOptional = allMethods.value.filter(m => !m.required && m.completed).length
  return completedOptional < props.minOptionalRequired
})

const getMethodLabel = (type: AuthMethodType): string => {
  const labels: Record<AuthMethodType, string> = {
    email: t('register.form.code'),
    sms: t('sms.title'),
    captcha: t('register.form.captcha'),
    discord: 'Discord'
  }
  return labels[type] || type
}

const getMethodClass = (method: MethodItem): string => {
  if (method.completed) {
    return 'bg-green-500/10 border border-green-500/30 text-green-300'
  }
  if (method.required) {
    return 'bg-red-500/10 border border-red-500/30 text-red-300'
  }
  return 'bg-white/5 border border-white/10 text-white/60'
}
</script>
