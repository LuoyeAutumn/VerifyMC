<template>
  <div class="auth-methods-section space-y-3">
    <VerificationProgress
      :must-methods="authMethodsState.mustMethods"
      :optional-methods="authMethodsState.optionalMethods"
      :min-optional-required="authMethodsState.minOptionalRequired"
      :completed-methods="completedMethods"
      :is-method-enabled="isMethodEnabled"
    />

    <div v-if="isMethodEnabled('email') && showEmailVerification">
      <Label for="code" class="mb-1 flex items-center gap-2">
        {{ $t('register.form.code') }}
        <span v-if="isMethodRequired('email')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
        <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
      </Label>
      <div class="flex flex-col sm:flex-row gap-2">
        <Input
          id="code"
          :model-value="form.code"
          type="text"
          :placeholder="$t('register.form.code_placeholder')"
          :class="{ 'border-red-500 focus-visible:ring-red-500': errors.code }"
          @update:model-value="onEmailCodeUpdate"
          @blur="validateCode"
        />
        <Button
          type="button"
          variant="secondary"
          @click="handleSendCode"
          :disabled="sending || !form.email || emailCooldownSeconds > 0"
          class="whitespace-nowrap"
        >
          {{ sending ? $t('register.sending') : emailCooldownSeconds > 0 ? `${emailCooldownSeconds}s` : $t('register.sendCode') }}
        </Button>
      </div>
      <p v-if="errors.code" class="mt-1 text-sm text-red-400">{{ errors.code }}</p>
    </div>

    <div v-if="isMethodEnabled('sms') && showSmsVerification">
      <Label class="mb-1 flex items-center gap-2">
        {{ $t('sms.title') }}
        <span v-if="isMethodRequired('sms')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
        <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
      </Label>
      <PhoneInput
        :model-value="form.phone"
        :country-code="form.countryCode"
        :code="form.smsCode"
        :country-codes="smsCountryCodes"
        :error="errors.phone"
        :code-error="errors.smsCode"
        @update:model-value="onPhoneUpdate"
        @update:country-code="onCountryCodeUpdate"
        @update:code="onSmsCodeUpdate"
      />
    </div>

    <div v-if="isMethodEnabled('captcha') && showCaptchaVerification">
      <Label for="captcha" class="mb-1 flex items-center gap-2">
        {{ $t('register.form.captcha') }}
        <span v-if="isMethodRequired('captcha')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
        <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
      </Label>
      <div class="flex flex-col sm:flex-row gap-2 items-center">
        <Input
          id="captcha"
          :model-value="form.captchaAnswer"
          type="text"
          :placeholder="$t('register.form.captcha_placeholder')"
          :class="{ 'border-red-500 focus-visible:ring-red-500': errors.captcha }"
          @update:model-value="onCaptchaAnswerUpdate"
          @blur="validateCaptcha"
        />
        <div
          class="cursor-pointer border border-white/10 rounded-lg overflow-hidden bg-white/5 backdrop-blur-sm hover:bg-white/15 hover:border-white/25 transition-all duration-300 flex-shrink-0 shadow-lg"
          @click="handleRefreshCaptcha"
          :title="$t('register.form.captcha_refresh')"
        >
          <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="h-10 w-auto" />
          <div v-else class="h-10 w-28 flex items-center justify-center text-white/60 text-sm">{{ $t('common.loading') }}</div>
        </div>
      </div>
      <p v-if="errors.captcha" class="mt-1 text-sm text-red-400">{{ errors.captcha }}</p>
      <p class="mt-1 text-xs text-white/50">{{ $t('register.form.captcha_hint') }}</p>
    </div>

    <div v-if="isMethodEnabled('discord') && showDiscordVerification" class="pt-2">
      <Label class="mb-2 flex items-center gap-2">
        Discord
        <span v-if="isMethodRequired('discord')" class="text-xs px-1.5 py-0.5 rounded bg-red-500/20 text-red-300">{{ $t('auth.required') }}</span>
        <span v-else class="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">{{ $t('auth.optional') }}</span>
      </Label>
      <DiscordLink
        :username="normalizedUsername"
        :required="isMethodRequired('discord')"
        @linked="onDiscordLinked"
        @unlinked="onDiscordUnlinked"
      />
      <p v-if="errors.discord" class="mt-1 text-sm text-red-400">{{ errors.discord }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AuthMethodType, AuthMethodsState } from '@/composables/useAuthMethods'
import type { ValidationForm, ValidationErrors } from '@/composables/useRegistrationValidation'
import VerificationProgress from '@/components/VerificationProgress.vue'
import PhoneInput from '@/components/PhoneInput.vue'
import DiscordLink from '@/components/DiscordLink.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Label from '@/components/ui/Label.vue'

interface Props {
  form: ValidationForm
  errors: ValidationErrors
  authMethodsState: AuthMethodsState
  isMethodEnabled: (type: AuthMethodType) => boolean
  isMethodRequired: (type: AuthMethodType) => boolean
  setMethodCompleted: (type: AuthMethodType, completed: boolean) => void
  completedMethods: AuthMethodType[]
  captchaImage: string
  captchaToken: string
  discordLinked: boolean
  smsCountryCodes: string[]
  normalizedUsername: string
  validateCode: () => void
  validateCaptcha: () => void
}

interface Emits {
  (e: 'update:form', value: Partial<ValidationForm>): void
  (e: 'update:code', value: string): void
  (e: 'update:phone', value: string): void
  (e: 'update:countryCode', value: string): void
  (e: 'update:smsCode', value: string): void
  (e: 'update:captchaAnswer', value: string): void
  (e: 'sendCode'): void
  (e: 'refreshCaptcha'): void
  (e: 'discordLinked'): void
  (e: 'discordUnlinked'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const { t } = useI18n()

const sending = ref(false)
const emailCooldownSeconds = ref(0)
const cooldownTimer = ref<ReturnType<typeof setInterval> | null>(null)

const showEmailVerification = computed(() => props.isMethodEnabled('email'))
const showSmsVerification = computed(() => props.isMethodEnabled('sms'))
const showCaptchaVerification = computed(() => props.isMethodEnabled('captcha'))
const showDiscordVerification = computed(() => props.isMethodEnabled('discord'))

const onEmailCodeUpdate = (value: string) => {
  emit('update:code', value)
  if (value && !props.errors.code) {
    props.setMethodCompleted('email', true)
  } else {
    props.setMethodCompleted('email', false)
  }
}

const onPhoneUpdate = (value: string) => {
  emit('update:phone', value)
}

const onCountryCodeUpdate = (value: string) => {
  emit('update:countryCode', value)
}

const onSmsCodeUpdate = (value: string) => {
  emit('update:smsCode', value)
  if (value && !props.errors.smsCode) {
    props.setMethodCompleted('sms', true)
  } else {
    props.setMethodCompleted('sms', false)
  }
}

const onCaptchaAnswerUpdate = (value: string) => {
  emit('update:captchaAnswer', value)
  if (value && !props.errors.captcha) {
    props.setMethodCompleted('captcha', true)
  } else {
    props.setMethodCompleted('captcha', false)
  }
}

const handleSendCode = () => {
  emit('sendCode')
}

const handleRefreshCaptcha = () => {
  emit('refreshCaptcha')
}

const onDiscordLinked = () => {
  emit('discordLinked')
  props.setMethodCompleted('discord', true)
}

const onDiscordUnlinked = () => {
  emit('discordUnlinked')
  props.setMethodCompleted('discord', false)
}

const startEmailCooldown = (seconds: number) => {
  emailCooldownSeconds.value = seconds
  if (cooldownTimer.value) clearInterval(cooldownTimer.value)
  cooldownTimer.value = setInterval(() => {
    emailCooldownSeconds.value--
    if (emailCooldownSeconds.value <= 0) {
      clearInterval(cooldownTimer.value!)
      cooldownTimer.value = null
    }
  }, 1000)
}

const stopEmailCooldown = () => {
  if (cooldownTimer.value) {
    clearInterval(cooldownTimer.value)
    cooldownTimer.value = null
  }
  emailCooldownSeconds.value = 0
}

onUnmounted(() => {
  stopEmailCooldown()
})

defineExpose({
  startEmailCooldown,
  stopEmailCooldown,
  setSending: (value: boolean) => {
    sending.value = value
  }
})
</script>
