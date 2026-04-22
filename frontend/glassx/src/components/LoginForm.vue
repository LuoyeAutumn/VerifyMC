<template>
  <div class="flex flex-col gap-6">
    <!-- 登录表单 -->
    <Card v-if="!showForgotPassword">
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
                type="text"
                :placeholder="$t('login.form.username_placeholder')"
                v-model="form.username"
              />
            </div>

            <div class="grid gap-2">
              <Label for="password">{{ $t('login.form.password') }}</Label>
              <Input
                id="password"
                type="password"
                :placeholder="$t('login.form.password_placeholder')"
                v-model="form.password"
              />
            </div>

            <Button
              type="submit"
              :disabled="loading"
              class="w-full"
            >
              <div v-if="loading" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <span>{{ loading ? $t('common.loading') : $t('login.form.submit') }}</span>
            </Button>

            <div class="text-center">
              <button
                type="button"
                class="text-sm text-blue-500 hover:text-blue-600 hover:underline"
                @click="showForgotPassword = true"
              >
                {{ $t('login.forgot_password.link') }}
              </button>
            </div>
          </div>
        </form>
      </CardContent>
    </Card>

    <!-- 忘记密码表单 -->
    <Card v-else>
      <CardHeader>
        <CardTitle class="text-2xl">{{ $t('login.forgot_password.title') }}</CardTitle>
        <CardDescription>{{ $t('login.forgot_password.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleForgotPassword">
          <div class="flex flex-col gap-6">
            <div class="grid gap-2">
              <Label for="forgot-email">{{ $t('login.forgot_password.email') }}</Label>
              <Input
                id="forgot-email"
                type="email"
                :placeholder="$t('login.forgot_password.email_placeholder')"
                v-model="forgotForm.email"
              />
            </div>

            <div class="grid gap-2">
              <Label for="forgot-code">{{ $t('login.forgot_password.code') }}</Label>
              <div class="flex gap-2">
                <Input
                  id="forgot-code"
                  type="text"
                  :placeholder="$t('login.forgot_password.code_placeholder')"
                  v-model="forgotForm.code"
                  maxlength="6"
                  class="flex-1"
                />
                <Button
                  type="button"
                  variant="outline"
                  :disabled="sendCodeLoading || cooldownSeconds > 0"
                  @click="handleSendCode"
                >
                  <span v-if="sendCodeLoading">{{ $t('register.sending') }}</span>
                  <span v-else-if="cooldownSeconds > 0">{{ cooldownSeconds }}s</span>
                  <span v-else>{{ $t('login.forgot_password.send_code') }}</span>
                </Button>
              </div>
            </div>

            <div class="grid gap-2">
              <Label for="forgot-password">{{ $t('login.forgot_password.new_password') }}</Label>
              <Input
                id="forgot-password"
                type="password"
                :placeholder="$t('login.forgot_password.new_password_placeholder')"
                v-model="forgotForm.password"
              />
            </div>

            <Button
              type="submit"
              :disabled="resetLoading"
              class="w-full"
            >
              <div v-if="resetLoading" class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></div>
              <span>{{ resetLoading ? $t('common.loading') : $t('login.forgot_password.reset') }}</span>
            </Button>

            <div class="text-center">
              <button
                type="button"
                class="text-sm text-blue-500 hover:text-blue-600 hover:underline"
                @click="showForgotPassword = false"
              >
                {{ $t('login.forgot_password.back_to_login') }}
              </button>
            </div>
          </div>
        </form>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotification } from '@/composables/useNotification'
import { apiService } from '@/services/api'
import { sessionService } from '@/services/session'
import Card from './ui/Card.vue'
import CardHeader from './ui/CardHeader.vue'
import CardTitle from './ui/CardTitle.vue'
import CardDescription from './ui/CardDescription.vue'
import CardContent from './ui/CardContent.vue'
import Button from './ui/Button.vue'
import Input from './ui/Input.vue'
import Label from './ui/Label.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const notification = useNotification()

const loading = ref(false)
const showForgotPassword = ref(false)
const sendCodeLoading = ref(false)
const resetLoading = ref(false)
const cooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

const redirectTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

const form = reactive({
  username: '',
  password: ''
})

const forgotForm = reactive({
  email: '',
  code: '',
  password: ''
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

const startCooldown = (seconds: number) => {
  cooldownSeconds.value = seconds
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
  }
  cooldownTimer = setInterval(() => {
    if (cooldownSeconds.value > 0) {
      cooldownSeconds.value--
    } else {
      if (cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!forgotForm.email.trim()) {
    notification.error(t('register.validation.email_required'))
    return
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(forgotForm.email.trim())) {
    notification.error(t('register.validation.email_format'))
    return
  }

  sendCodeLoading.value = true

  try {
    const response = await apiService.forgotPasswordSendCode({
      email: forgotForm.email.trim(),
      language: locale.value
    })

    if (response.success) {
      notification.success(t('login.forgot_password.code_sent'))
      if (response.remainingSeconds) {
        startCooldown(response.remainingSeconds)
      } else {
        startCooldown(60)
      }
    } else {
      notification.error(response.message || t('register.sendFailed'))
      if (response.remainingSeconds) {
        startCooldown(response.remainingSeconds)
      }
    }
  } catch {
    notification.error(t('register.sendFailed'))
  } finally {
    sendCodeLoading.value = false
  }
}

const handleForgotPassword = async () => {
  if (!forgotForm.email.trim()) {
    notification.error(t('register.validation.email_required'))
    return
  }

  if (!forgotForm.code.trim() || forgotForm.code.length !== 6) {
    notification.error(t('register.validation.code_required'))
    return
  }

  if (!forgotForm.password.trim()) {
    notification.error(t('register.validation.password_required'))
    return
  }

  resetLoading.value = true

  try {
    const response = await apiService.forgotPasswordReset({
      email: forgotForm.email.trim(),
      code: forgotForm.code.trim(),
      password: forgotForm.password,
      language: locale.value
    })

    if (response.success) {
      notification.success(t('login.forgot_password.reset_success'))
      forgotForm.email = ''
      forgotForm.code = ''
      forgotForm.password = ''
      showForgotPassword.value = false
    } else {
      notification.error(response.message || t('login.forgot_password.reset_failed'))
    }
  } catch {
    notification.error(t('login.forgot_password.reset_failed'))
  } finally {
    resetLoading.value = false
  }
}

onUnmounted(() => {
  if (redirectTimeout.value) {
    clearTimeout(redirectTimeout.value)
    redirectTimeout.value = null
  }
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
})
</script> 
