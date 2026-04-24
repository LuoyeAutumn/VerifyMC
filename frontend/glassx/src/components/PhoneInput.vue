<template>
  <div class="phone-input space-y-2">
    <div class="flex flex-col sm:flex-row gap-2">
      <div class="relative sm:w-32 flex-shrink-0">
        <select
          v-model="selectedCountryCode"
          :disabled="disabled"
          class="glass-input w-full h-10 appearance-none pr-8 cursor-pointer"
          :class="{ 'opacity-50 cursor-not-allowed': disabled }"
        >
          <option v-for="code in availableCountryCodes" :key="code" :value="code">
            {{ code }}
          </option>
        </select>
        <div class="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none text-white/50">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>

      <Input
        v-model="phoneNumber"
        type="tel"
        :placeholder="$t('sms.phonePlaceholder')"
        :disabled="disabled"
        :class="{ 'border-red-500 focus-visible:ring-red-500': error }"
        class="flex-1"
      />
    </div>

    <div class="flex flex-col sm:flex-row gap-2">
      <Input
        v-model="verificationCode"
        type="text"
        :placeholder="$t('sms.codePlaceholder')"
        :disabled="disabled || !isPhoneValid"
        :class="{ 'border-red-500 focus-visible:ring-red-500': codeError }"
        class="flex-1"
      />
      <Button
        type="button"
        variant="secondary"
        :disabled="disabled || sending || !isPhoneValid || cooldownSeconds > 0"
        @click="handleSendCode"
        class="whitespace-nowrap"
      >
        {{ sending ? $t('register.sending') : cooldownSeconds > 0 ? $t('sms.resend', { seconds: cooldownSeconds }) : $t('sms.sendCode') }}
      </Button>
    </div>

    <p v-if="error" class="text-sm text-red-400">{{ error }}</p>
    <div v-if="localizedCodeError || remainingAttemptsText" class="space-y-1">
      <p v-if="localizedCodeError" class="text-sm text-red-400">{{ localizedCodeError }}</p>
      <p v-if="remainingAttemptsText" class="text-sm text-yellow-400">{{ remainingAttemptsText }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { apiService } from '@/services/api'
import { useNotification } from '@/composables/useNotification'
import { useCooldown } from '@/composables/useCooldown'
import Input from './ui/Input.vue'
import Button from './ui/Button.vue'

interface Props {
  modelValue?: string
  countryCode?: string
  code?: string
  disabled?: boolean
  countryCodes?: string[]
  error?: string
  codeError?: string
  phoneRegex?: string
  errorCode?: string
  remainingAttempts?: number
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'update:countryCode', value: string): void
  (e: 'update:code', value: string): void
  (e: 'verified', data: { phone: string; countryCode: string; code: string }): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  countryCode: '+86',
  code: '',
  disabled: false,
  countryCodes: () => ['+86', '+1', '+44', '+81', '+82', '+852', '+853', '+886'],
  error: '',
  codeError: '',
  errorCode: '',
  remainingAttempts: undefined
})

const emit = defineEmits<Emits>()

const { t, locale } = useI18n()
const { success, error: showError } = useNotification()

const phoneNumber = ref(props.modelValue)
const selectedCountryCode = ref(props.countryCode)
const verificationCode = ref(props.code)
const sending = ref(false)

const { cooldownSeconds, startCooldown, stopCooldown } = useCooldown()

const availableCountryCodes = computed(() => {
  return props.countryCodes.length > 0 ? props.countryCodes : ['+86', '+1', '+44', '+81', '+82', '+852', '+853', '+886']
})

const isPhoneValid = computed(() => {
  const phone = phoneNumber.value.trim()
  if (!phone) return false
  const regex = props.phoneRegex ? new RegExp(props.phoneRegex) : /^\d{6,15}$/
  return regex.test(phone.replace(/[\s-]/g, ''))
})

const errorCodeMap: Record<string, string> = {
  CODE_INVALID: 'sms.code_invalid',
  CODE_EXPIRED: 'sms.code_expired',
  CODE_ATTEMPTS_EXCEEDED: 'sms.code_attempts_exceeded',
  RATE_LIMITED: 'sms.rateLimited'
}

const localizedCodeError = computed(() => {
  if (props.errorCode && errorCodeMap[props.errorCode]) {
    return t(errorCodeMap[props.errorCode])
  }
  return props.codeError
})

const remainingAttemptsText = computed(() => {
  if (props.remainingAttempts !== undefined && props.remainingAttempts > 0) {
    return t('sms.remaining_attempts', { count: props.remainingAttempts })
  }
  return ''
})

watch(phoneNumber, (value) => {
  emit('update:modelValue', value)
})

watch(selectedCountryCode, (value) => {
  emit('update:countryCode', value)
})

watch(verificationCode, (value) => {
  emit('update:code', value)
})

watch(() => props.modelValue, (value) => {
  phoneNumber.value = value
})

watch(() => props.countryCode, (value) => {
  selectedCountryCode.value = value
})

watch(() => props.code, (value) => {
  verificationCode.value = value
})

const handleSendCode = async () => {
  if (sending.value || cooldownSeconds.value > 0 || !isPhoneValid.value) return

  sending.value = true
  try {
    const phone = phoneNumber.value.trim().replace(/[\s-]/g, '')
    const countryCode = selectedCountryCode.value

    const res = await apiService.sendSmsCode({
      phone,
      countryCode,
      language: locale.value === 'zh' ? 'zh' : 'en'
    })

    if (res.success) {
      success(t('sms.sent'))
      startCooldown(res.remainingSeconds || 60)
    } else {
      showError(res.message || t('sms.failed'))
    }
  } catch (e) {
    console.error('Failed to send SMS code:', e)
    showError(t('sms.failed'))
  } finally {
    sending.value = false
  }
}

onUnmounted(() => {
  stopCooldown()
})
</script>
