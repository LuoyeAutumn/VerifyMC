<template>
  <div class="flex flex-col gap-6">
    <Card>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.title') }}</CardTitle>
        <CardDescription>{{ $t('login.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit">
          <div class="flex flex-col gap-6">
            <div class="grid gap-2">
              <Label for="username">{{ $t('login.form.username') }}</Label>
              <Input
                id="username"
                v-model="form.username"
                type="text"
                :placeholder="$t('login.form.username_placeholder')"
              />
            </div>

            <div class="grid gap-2">
              <Label for="password">{{ $t('login.form.password') }}</Label>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                :placeholder="$t('login.form.password_placeholder')"
              />
            </div>

            <div v-if="forgotPasswordEnabled" class="flex justify-end">
              <button
                type="button"
                class="text-sm text-blue-200 hover:text-blue-100 transition-colors"
                @click="openForgotPasswordDialog"
              >
                {{ $t('login.forgot_password.open') }}
              </button>
            </div>

            <Button
              type="submit"
              :disabled="loading"
              class="w-full"
            >
              <div v-if="loading" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <span>{{ loading ? $t('common.loading') : $t('login.form.submit') }}</span>
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>

    <Dialog
      :show="showForgotPasswordDialog"
      :title="$t('login.forgot_password.title')"
      @close="closeForgotPasswordDialog"
    >
      <div class="space-y-4">
        <div class="grid gap-2">
          <Label for="forgot-email">{{ $t('login.forgot_password.email') }}</Label>
          <Input
            id="forgot-email"
            v-model="forgotForm.email"
            type="email"
            :placeholder="$t('login.forgot_password.email_placeholder')"
          />
        </div>

        <div class="grid gap-2">
          <Label for="forgot-code">{{ $t('login.forgot_password.code') }}</Label>
          <div class="flex flex-col sm:flex-row gap-2">
            <Input
              id="forgot-code"
              v-model="forgotForm.code"
              type="text"
              :placeholder="$t('login.forgot_password.code_placeholder')"
            />
            <Button
              type="button"
              variant="secondary"
              class="whitespace-nowrap"
              :disabled="sendingForgotCode || forgotCodeCooldownSeconds > 0"
              @click="sendForgotPasswordCode"
            >
              <div v-if="sendingForgotCode" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              {{ sendingForgotCode ? $t('common.loading') : forgotCodeCooldownSeconds > 0 ? `${forgotCodeCooldownSeconds}s` : $t('login.forgot_password.send_code') }}
            </Button>
          </div>
        </div>

        <div class="grid gap-2">
          <Label for="forgot-new-password">{{ $t('login.forgot_password.new_password') }}</Label>
          <Input
            id="forgot-new-password"
            v-model="forgotForm.newPassword"
            type="password"
            :placeholder="$t('login.forgot_password.new_password_placeholder')"
          />
        </div>

        <div v-if="forgotPasswordCaptchaEnabled" class="grid gap-2">
          <Label for="forgot-captcha">{{ $t('register.form.captcha') }}</Label>
          <div class="flex flex-col sm:flex-row gap-2 items-center">
            <Input
              id="forgot-captcha"
              v-model="forgotForm.captchaAnswer"
              type="text"
              :placeholder="$t('register.form.captcha_placeholder')"
            />
            <div
              class="cursor-pointer border border-white/10 rounded-lg overflow-hidden bg-white/5 backdrop-blur-sm hover:bg-white/15 hover:border-white/25 transition-all duration-300 flex-shrink-0 shadow-lg"
              @click="refreshForgotCaptcha"
            >
              <img v-if="forgotCaptchaImage" :src="forgotCaptchaImage" alt="forgot-captcha" class="h-10 w-auto" />
              <div v-else class="h-10 w-28 flex items-center justify-center text-white/60 text-sm">{{ $t('common.loading') }}</div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button variant="outline" @click="closeForgotPasswordDialog">
          {{ $t('common.cancel') }}
        </Button>
        <Button :disabled="resettingForgotPassword" @click="resetForgottenPassword">
          <div v-if="resettingForgotPassword" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
          {{ resettingForgotPassword ? $t('common.loading') : $t('login.forgot_password.submit') }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onUnmounted, inject, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { useCooldown } from '@/composables/useCooldown'
import { apiService, type ConfigResponse } from '@/services/api'
import { sessionService } from '@/services/session'
import Card from './ui/Card.vue'
import CardHeader from './ui/CardHeader.vue'
import CardTitle from './ui/CardTitle.vue'
import CardDescription from './ui/CardDescription.vue'
import CardContent from './ui/CardContent.vue'
import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'
import Dialog from './ui/Dialog.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const notification = useNotification()
const config = inject<{ value: ConfigResponse }>('config', { value: {} as ConfigResponse })

const loading = ref(false)
const showForgotPasswordDialog = ref(false)
const sendingForgotCode = ref(false)
const resettingForgotPassword = ref(false)
const forgotCaptchaImage = ref('')
const forgotCaptchaToken = ref('')
const { cooldownSeconds: forgotCodeCooldownSeconds, startCooldown: startForgotCodeCooldown } = useCooldown()

const forgotPasswordEnabled = computed(() => !!config.value?.forgotPassword?.enabled)
const forgotPasswordCaptchaEnabled = computed(() => !!config.value?.forgotPassword?.captchaEnabled)

const redirectTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

const form = reactive({
  username: '',
  password: ''
})

const forgotForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  captchaAnswer: ''
})

const errors = reactive({
  username: '',
  password: ''
})

const validateForm = () => {
  errors.username = ''
  errors.password = ''

  const username = form.username.trim()
  const password = form.password.trim()

  let isValid = true

  if (!username) {
    errors.username = t('login.validation.username_required')
    isValid = false
  }

  if (!password) {
    errors.password = t('login.validation.password_required')
    isValid = false
  }

  return isValid
}

const handleSubmit = async () => {
  if (!validateForm()) {
    const firstError = errors.username || errors.password
    if (firstError) {
      notification.error(firstError)
    }
    return
  }

  loading.value = true

  try {
    const response = await apiService.adminLogin({
      username: form.username.trim(),
      password: form.password,
      language: locale.value
    })

    if (response.success) {
      sessionService.setToken(response.token)
      sessionService.setUserInfo({
        username: response.username || form.username.trim(),
        isAdmin: response.isAdmin ?? false
      })

      notification.success(response.message || t('login.messages.success'))
      const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/dashboard'

      redirectTimeout.value = setTimeout(() => {
        router.push(redirect)
      }, 1000)
    } else {
      notification.error(response.message || t('login.messages.error'))
    }

  } catch {
    notification.error(t('login.messages.invalid_credentials'))
  } finally {
    loading.value = false
  }
}

const refreshForgotCaptcha = async () => {
  if (!forgotPasswordCaptchaEnabled.value) return
  try {
    const response = await apiService.getCaptcha()
    if (response.success && response.token && response.image) {
      forgotCaptchaToken.value = response.token
      forgotCaptchaImage.value = response.image
    }
  } catch {
    forgotCaptchaToken.value = ''
    forgotCaptchaImage.value = ''
  }
}

const openForgotPasswordDialog = async () => {
  showForgotPasswordDialog.value = true
  if (forgotPasswordCaptchaEnabled.value) {
    await refreshForgotCaptcha()
  }
}

const closeForgotPasswordDialog = () => {
  showForgotPasswordDialog.value = false
}

const validateForgotPasswordForm = (forSendCode: boolean) => {
  if (!forgotForm.email.trim()) {
    notification.error(t('login.forgot_password.email_required'))
    return false
  }
  if (forSendCode) {
    if (forgotPasswordCaptchaEnabled.value && (!forgotCaptchaToken.value || !forgotForm.captchaAnswer.trim())) {
      notification.error(t('register.validation.captcha_required'))
      return false
    }
    return true
  }
  if (!forgotForm.code.trim()) {
    notification.error(t('login.forgot_password.code_required'))
    return false
  }
  if (!forgotForm.newPassword) {
    notification.error(t('login.forgot_password.new_password_required'))
    return false
  }
  if (forgotPasswordCaptchaEnabled.value && (!forgotCaptchaToken.value || !forgotForm.captchaAnswer.trim())) {
    notification.error(t('register.validation.captcha_required'))
    return false
  }
  return true
}

const sendForgotPasswordCode = async () => {
  if (sendingForgotCode.value || forgotCodeCooldownSeconds.value > 0) return
  if (!validateForgotPasswordForm(true)) return

  sendingForgotCode.value = true
  try {
    const response = await apiService.sendForgotPasswordCode({
      email: forgotForm.email.trim().toLowerCase(),
      language: locale.value,
      captchaToken: forgotPasswordCaptchaEnabled.value ? forgotCaptchaToken.value : undefined,
      captchaAnswer: forgotPasswordCaptchaEnabled.value ? forgotForm.captchaAnswer.trim() : undefined,
    })

    if (response.success) {
      notification.success(response.message || t('login.forgot_password.code_sent'))
      if (response.multipleAccounts) {
        notification.warning(t('login.forgot_password.multiple_accounts_warning'))
      }
      startForgotCodeCooldown(response.remainingSeconds || 60)
    } else if (response.remainingSeconds && response.remainingSeconds > 0) {
      startForgotCodeCooldown(response.remainingSeconds)
      notification.error(response.message || t('login.forgot_password.code_send_failed'))
    } else {
      notification.error(response.message || t('login.forgot_password.code_send_failed'))
    }
  } catch {
    notification.error(t('login.forgot_password.code_send_failed'))
  } finally {
    if (forgotPasswordCaptchaEnabled.value) {
      forgotForm.captchaAnswer = ''
      await refreshForgotCaptcha()
    }
    sendingForgotCode.value = false
  }
}

const resetForgottenPassword = async () => {
  if (resettingForgotPassword.value) return
  if (!validateForgotPasswordForm(false)) return

  resettingForgotPassword.value = true
  try {
    const response = await apiService.resetForgottenPassword({
      email: forgotForm.email.trim().toLowerCase(),
      code: forgotForm.code.trim(),
      newPassword: forgotForm.newPassword,
      language: locale.value,
      captchaToken: forgotPasswordCaptchaEnabled.value ? forgotCaptchaToken.value : undefined,
      captchaAnswer: forgotPasswordCaptchaEnabled.value ? forgotForm.captchaAnswer.trim() : undefined,
    })

    if (response.success) {
      notification.success(response.message || t('login.forgot_password.reset_success'))
      forgotForm.code = ''
      forgotForm.newPassword = ''
      forgotForm.captchaAnswer = ''
      showForgotPasswordDialog.value = false
    } else {
      notification.error(response.message || t('login.forgot_password.reset_failed'))
    }
  } catch {
    notification.error(t('login.forgot_password.reset_failed'))
  } finally {
    if (forgotPasswordCaptchaEnabled.value) {
      forgotForm.captchaAnswer = ''
      await refreshForgotCaptcha()
    }
    resettingForgotPassword.value = false
  }
}

onUnmounted(() => {
  if (redirectTimeout.value) {
    clearTimeout(redirectTimeout.value)
    redirectTimeout.value = null
  }
})
</script>
