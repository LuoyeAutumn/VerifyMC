<template>
  <div class="space-y-4">
    <div class="rounded-lg border border-white/10 bg-white/5 p-4 text-sm text-white/80">
      <p class="mb-1">
        <strong>{{ $t('register.summary.username') }}:</strong> {{ username }}
      </p>
      <p class="mb-1">
        <strong>{{ $t('register.summary.email') }}:</strong> {{ email }}
      </p>
      <p v-if="questionnaireResult">
        <strong>{{ $t('register.summary.questionnaire') }}:</strong>
        {{ questionnaireResult.passed ? $t('questionnaire.passed') : $t('questionnaire.failed') }}
        ({{ questionnaireResult.score }}/{{ questionnaireResult.passScore }})
      </p>
    </div>

    <div class="rounded-lg border border-white/10 bg-white/5 p-6 text-center">
      <div v-if="loading" class="flex flex-col items-center gap-3 text-white/70">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
        <p>{{ $t('common.loading') }}</p>
      </div>
      <p v-else-if="registrationSubmitted" class="text-green-300 font-medium">
        {{ successMessage }}
      </p>
      <p v-else class="text-red-300 font-medium">{{ $t('register.failed') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { QuestionnaireSubmission } from '@/services/api'

interface Props {
  username: string
  email: string
  loading: boolean
  registrationSubmitted: boolean
  successMessage: string
  questionnaireResult?: QuestionnaireSubmission | null
}

withDefaults(defineProps<Props>(), {
  username: '',
  email: '',
  loading: false,
  registrationSubmitted: false,
  successMessage: '',
  questionnaireResult: null
})
</script>
