<template>
  <div
    class="w-full flex flex-col items-center"
    role="navigation"
    :aria-label="`步骤导航，共 ${totalSteps} 步，当前第 ${currentStep} 步`"
  >
    <div class="w-full mb-8">
      <ol class="flex items-center justify-center" role="list">
        <template v-for="(step, index) in totalSteps" :key="index">
          <StepIndicator
            :step="index + 1"
            :current-step="currentStep"
            :disable-step-indicators="disableStepIndicators"
            @click-step="handleStepClick"
          />
          <StepConnector
            v-if="index < totalSteps - 1"
            :is-complete="index + 1 < currentStep"
          />
        </template>
      </ol>
    </div>

    <StepContentWrapper :direction="direction" :content-key="currentStep" class="w-full">
      <div class="w-full" role="region" :aria-label="`步骤 ${currentStep} 内容`">
        <slot :currentStep="currentStep" :totalSteps="totalSteps" />
      </div>
    </StepContentWrapper>

    <div v-if="!isCompleted" class="w-full px-8 pb-8 pt-4">
      <div class="flex items-center" :class="currentStep !== 1 ? 'justify-between' : 'justify-end'">
        <button
          v-if="currentStep !== 1"
          @click="handleBack"
          class="text-sm font-medium transition-all duration-300 text-muted-foreground hover:text-foreground px-4 py-2 rounded-xl bg-white/5 backdrop-blur-sm border border-white/10 hover:bg-white/10 hover:border-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          :aria-label="`返回到第 ${currentStep - 1} 步`"
        >
          {{ backButtonText }}
        </button>
        <button
          @click="isLastStep ? handleComplete : handleNext"
          class="inline-flex h-11 items-center justify-center rounded-full bg-primary/80 backdrop-blur-sm px-8 text-sm font-semibold tracking-tight text-primary-foreground transition-all duration-300 hover:bg-primary hover:shadow-lg hover:shadow-primary/30 active:scale-95 border border-primary/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          :aria-label="isLastStep ? '完成所有步骤' : `前进到第 ${currentStep + 1} 步`"
        >
          {{ isLastStep ? 'Complete' : nextButtonText }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, useSlots, defineComponent, h, type VNode, type PropType } from 'vue'
import { Check } from 'lucide-vue-next'
import StepContentWrapper from './StepContentWrapper.vue'

type StepStatus = 'inactive' | 'active' | 'complete'

interface Props {
  initialStep?: number
  totalSteps?: number
  backButtonText?: string
  nextButtonText?: string
  disableStepIndicators?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  initialStep: 1,
  totalSteps: 0,
  backButtonText: 'Back',
  nextButtonText: 'Continue',
  disableStepIndicators: false
})

const emit = defineEmits<{
  (e: 'stepChange', step: number): void
  (e: 'finalStepCompleted'): void
}>()

const slots = useSlots()

const currentStep = ref(props.initialStep)
const direction = ref<'forward' | 'backward'>('forward')

const countSlotChildren = (slot: (() => VNode[]) | undefined): number => {
  if (!slot) return 0
  const children = slot()
  return children.filter((child) => child.type !== Comment && child.type.toString() !== 'Symbol(Comment)').length
}

const totalSteps = computed(() => {
  if (props.totalSteps > 0) {
    return props.totalSteps
  }
  if (slots.default) {
    return countSlotChildren(slots.default)
  }
  return 0
})

const isCompleted = computed(() => currentStep.value > totalSteps.value)

const isLastStep = computed(() => currentStep.value === totalSteps.value)

const updateStep = (newStep: number) => {
  if (newStep < 1 || newStep > totalSteps.value + 1) return

  direction.value = newStep > currentStep.value ? 'forward' : 'backward'
  currentStep.value = newStep

  emit('stepChange', newStep)

  if (newStep > totalSteps.value) {
    emit('finalStepCompleted')
  }
}

const handleBack = () => {
  if (currentStep.value > 1) {
    updateStep(currentStep.value - 1)
  }
}

const handleNext = () => {
  if (currentStep.value <= totalSteps.value) {
    updateStep(currentStep.value + 1)
  }
}

const handleComplete = () => {
  updateStep(totalSteps.value + 1)
}

const handleStepClick = (step: number) => {
  if (step <= currentStep.value) {
    updateStep(step)
  }
}

const getStepStatus = (step: number, current: number): StepStatus => {
  if (current === step) return 'active'
  if (current < step) return 'inactive'
  return 'complete'
}

const StepIndicator = defineComponent({
  name: 'StepIndicator',
  props: {
    step: { type: Number, required: true },
    currentStep: { type: Number, required: true },
    disableStepIndicators: { type: Boolean, default: false }
  },
  emits: ['clickStep'],
  setup(props, { emit }) {
    const status = computed<StepStatus>(() => getStepStatus(props.step, props.currentStep))
    
    const handleClick = () => {
      if (!props.disableStepIndicators) {
        emit('clickStep', props.step)
      }
    }

    const handleKeydown = (event: KeyboardEvent) => {
      if (props.disableStepIndicators) return
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        emit('clickStep', props.step)
      }
    }

    const getAriaLabel = () => {
      const stepText = `第 ${props.step} 步`
      switch (status.value) {
        case 'active':
          return `${stepText}，当前步骤`
        case 'complete':
          return `${stepText}，已完成`
        default:
          return `${stepText}，未完成`
      }
    }

    const indicatorClasses = computed(() => {
      const baseClasses = 'flex h-10 w-10 items-center justify-center rounded-full border-2 font-semibold transition-all duration-300 backdrop-blur-sm'
      
      switch (status.value) {
        case 'inactive':
          return `${baseClasses} bg-white/5 text-muted-foreground border-white/10 hover:bg-white/10 hover:border-white/20`
        case 'active':
          return `${baseClasses} bg-white/10 text-primary border-primary shadow-lg shadow-primary/20`
        case 'complete':
          return `${baseClasses} bg-primary/80 text-primary-foreground border-primary shadow-lg shadow-primary/30`
        default:
          return baseClasses
      }
    })

    return () => h('li', {
      class: 'relative flex items-center justify-center',
      role: 'listitem'
    }, [
      h('div', {
        class: indicatorClasses.value,
        role: 'button',
        tabindex: props.disableStepIndicators ? -1 : 0,
        'aria-current': status.value === 'active' ? 'step' : undefined,
        'aria-label': getAriaLabel(),
        'aria-disabled': props.disableStepIndicators ? 'true' : undefined,
        onClick: handleClick,
        onKeydown: handleKeydown
      }, [
        status.value === 'complete'
          ? h(Check, { class: 'h-5 w-5', 'aria-hidden': 'true' })
          : h('span', { class: 'text-sm', 'aria-hidden': 'true' }, props.step)
      ]),
      status.value === 'active'
        ? h('div', {
            class: 'absolute -inset-1 rounded-full bg-primary/20 blur-sm animate-pulse',
            'aria-hidden': 'true'
          })
        : null
    ])
  }
})

const StepConnector = defineComponent({
  name: 'StepConnector',
  props: {
    isComplete: { type: Boolean, default: false }
  },
  setup(props) {
    return () => h('div', {
      class: 'relative mx-4 h-0.5 flex-1 overflow-hidden rounded-full bg-white/10 backdrop-blur-sm'
    }, [
      h('div', {
        class: 'absolute inset-0 bg-gradient-to-r from-primary to-primary/80 origin-left transition-transform duration-500 ease-[cubic-bezier(0.33,1,0.68,1)] shadow-sm shadow-primary/30',
        style: { transform: props.isComplete ? 'scaleX(1)' : 'scaleX(0)' }
      })
    ])
  }
})

defineExpose({
  currentStep,
  totalSteps,
  isCompleted,
  isLastStep,
  updateStep,
  handleBack,
  handleNext,
  handleComplete
})
</script>

<style scoped>
@media (prefers-reduced-motion: reduce) {
  .animate-pulse {
    animation: none !important;
  }
}
</style>
