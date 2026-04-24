<template>
  <form @submit.prevent="handleSubmit" class="space-y-5">
    <div class="space-y-3">
      <PlatformSelector
        v-if="bedrockEnabled"
        v-model="localPlatform"
        :bedrock-enabled="bedrockEnabled"
        :bedrock-prefix="bedrockPrefix"
        :username="localUsername"
        :label="$t('register.form.platform')"
        :java-label="$t('register.form.platform_java')"
        :bedrock-label="$t('register.form.platform_bedrock')"
        @update:username="handleUsernameUpdate"
        @platform-change="handlePlatformChange"
      />

      <div>
        <Label for="username" class="mb-1">{{ $t('register.form.username') }}</Label>
        <Input
          id="username"
          v-model="localUsername"
          type="text"
          :placeholder="$t('register.form.username_placeholder')"
          :class="{ 'border-red-500 focus-visible:ring-red-500': errors.username }"
          @blur="validateUsername"
        />
        <p v-if="errors.username" class="mt-1 text-sm text-red-400">{{ errors.username }}</p>
      </div>

      <div>
        <Label for="password" class="mb-1">{{ $t('register.form.password') }}</Label>
        <Input
          id="password"
          v-model="localPassword"
          type="password"
          :placeholder="$t('register.form.password_placeholder')"
          :class="{ 'border-red-500 focus-visible:ring-red-500': errors.password }"
          @blur="validatePassword"
        />
        <p v-if="errors.password" class="mt-1 text-sm text-red-400">{{ errors.password }}</p>
        <p v-if="passwordHint" class="mt-1 text-xs text-white/50">{{ passwordHint }}</p>
      </div>
    </div>

    <Button type="submit" :disabled="!isValid" class="w-full">
      <slot name="submit-text">{{ $t('register.actions.next') }}</slot>
    </Button>
  </form>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PlatformSelector, { type Platform } from '@/components/registration/PlatformSelector.vue'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Label from '@/components/ui/Label.vue'
import {
  useRegistrationValidation,
  createValidationErrors,
  type ValidationConfig,
  type ValidationForm,
  type ValidationErrors
} from '@/composables/useRegistrationValidation'

export interface BasicInfoFormData {
  username: string
  email: string
  password: string
  platform: Platform
}

const props = withDefaults(defineProps<{
  bedrockEnabled?: boolean
  bedrockPrefix?: string
  usernameRegex?: string
  passwordRegex?: string
  username?: string
  email?: string
  password?: string
  platform?: Platform
}>(), {
  bedrockEnabled: false,
  bedrockPrefix: '.',
  usernameRegex: '',
  passwordRegex: '',
  username: '',
  email: '',
  password: '',
  platform: 'java'
})

const emit = defineEmits<{
  'submit': [data: BasicInfoFormData]
  'update:username': [value: string]
  'update:email': [value: string]
  'update:password': [value: string]
  'update:platform': [value: Platform]
  'validation-change': [isValid: boolean]
}>()

const { t } = useI18n()

const localPlatform = ref<Platform>(props.platform)
const discordLinked = ref(false)

const localUsername = ref(props.username)
const localEmail = ref(props.email)
const localPassword = ref(props.password)

const config = computed<ValidationConfig>(() => ({
  usernameRegex: props.usernameRegex,
  passwordRegex: props.passwordRegex,
  bedrockEnabled: props.bedrockEnabled,
  bedrockPrefix: props.bedrockPrefix
}))

const form = computed<ValidationForm>(() => ({
  username: localUsername.value,
  email: localEmail.value,
  password: localPassword.value,
  code: '',
  captchaAnswer: '',
  phone: '',
  countryCode: '+86',
  smsCode: ''
}))

const errors = reactive<ValidationErrors>(createValidationErrors())

const methodCheckers = {
  isMethodEnabled: () => false,
  isMethodRequired: () => false
}

const platformState = {
  selectedPlatform: localPlatform,
  discordLinked
}

const validationForm = reactive<ValidationForm>({
  username: localUsername.value,
  email: localEmail.value,
  password: localPassword.value,
  code: '',
  captchaAnswer: '',
  phone: '',
  countryCode: '+86',
  smsCode: ''
})

watch(localUsername, (val) => {
  validationForm.username = val
})

watch(localEmail, (val) => {
  validationForm.email = val
})

watch(localPassword, (val) => {
  validationForm.password = val
})

const {
  validateUsername,
  validateEmail,
  validatePassword,
  getNormalizedUsername,
  clearErrors
} = useRegistrationValidation({
  config,
  form: validationForm,
  errors,
  methodCheckers,
  platformState
})

const passwordHint = computed(() => {
  if (props.passwordRegex) {
    return t('register.form.password_hint', { regex: props.passwordRegex })
  }
  return ''
})

const isValid = computed(() => {
  return !!(
    localUsername.value &&
    localPassword.value &&
    !errors.username &&
    !errors.password
  )
})

watch(isValid, (newValue) => {
  emit('validation-change', newValue)
})

watch(localUsername, (newValue) => {
  emit('update:username', newValue)
})

watch(localEmail, (newValue) => {
  emit('update:email', newValue)
})

watch(localPassword, (newValue) => {
  emit('update:password', newValue)
})

watch(localPlatform, (newValue) => {
  emit('update:platform', newValue)
})

watch(() => props.username, (newValue) => {
  if (newValue !== localUsername.value) {
    localUsername.value = newValue
  }
})

watch(() => props.email, (newValue) => {
  if (newValue !== localEmail.value) {
    localEmail.value = newValue
  }
})

watch(() => props.password, (newValue) => {
  if (newValue !== localPassword.value) {
    localPassword.value = newValue
  }
})

watch(() => props.platform, (newValue) => {
  if (newValue !== localPlatform.value) {
    localPlatform.value = newValue
  }
})

const handleUsernameUpdate = (username: string) => {
  localUsername.value = username
}

const handlePlatformChange = (platform: Platform) => {
  localPlatform.value = platform
}

const handleSubmit = () => {
  validateUsername()
  validatePassword()

  if (!isValid.value) return

  emit('submit', {
    username: getNormalizedUsername(),
    email: localEmail.value.trim(),
    password: localPassword.value,
    platform: localPlatform.value
  })
}

defineExpose({
  validateUsername,
  validateEmail,
  validatePassword,
  clearErrors,
  getNormalizedUsername,
  form: validationForm,
  errors,
  isValid
})
</script>
