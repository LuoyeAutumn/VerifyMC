<script setup lang="ts">
import { ref, onMounted, watch, nextTick, computed, onUnmounted } from 'vue'

interface Props {
  direction?: 'forward' | 'backward'
  contentKey?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  direction: 'forward',
  contentKey: 0
})

const contentRef = ref<HTMLDivElement | null>(null)
const wrapperRef = ref<HTMLDivElement | null>(null)
const contentHeight = ref<number | 'auto'>('auto')
const isAnimating = ref(false)

const transitionName = computed(() => {
  return props.direction === 'forward' ? 'slide-content-left' : 'slide-content-right'
})

const measureHeight = () => {
  if (contentRef.value) {
    const height = contentRef.value.scrollHeight
    if (height > 0) {
      contentHeight.value = height
    }
  }
}

const handleBeforeEnter = () => {
  isAnimating.value = true
}

const handleEnter = async () => {
  await nextTick()
  requestAnimationFrame(() => {
    measureHeight()
  })
}

const handleAfterEnter = () => {
  isAnimating.value = false
  contentHeight.value = 'auto'
}

const handleBeforeLeave = () => {
  if (contentRef.value) {
    contentHeight.value = contentRef.value.scrollHeight
  }
  isAnimating.value = true
}

const handleAfterLeave = () => {
  isAnimating.value = false
}

onMounted(() => {
  nextTick(() => {
    contentHeight.value = 'auto'
  })
})

watch(() => props.contentKey, () => {
  nextTick(() => {
    contentHeight.value = 'auto'
  })
})
</script>

<template>
  <div
    ref="wrapperRef"
    class="step-content-wrapper"
    :style="{
      height: contentHeight === 'auto' ? 'auto' : `${contentHeight}px`
    }"
  >
    <Transition
      :name="transitionName"
      mode="out-in"
      @before-enter="handleBeforeEnter"
      @enter="handleEnter"
      @after-enter="handleAfterEnter"
      @before-leave="handleBeforeLeave"
      @after-leave="handleAfterLeave"
    >
      <div
        ref="contentRef"
        :key="contentKey"
        class="step-content-inner"
      >
        <slot />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.step-content-wrapper {
  width: 100%;
  position: relative;
  overflow: hidden;
  transition: height 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.step-content-inner {
  width: 100%;
  position: relative;
}

.slide-content-left-enter-active,
.slide-content-left-leave-active,
.slide-content-right-enter-active,
.slide-content-right-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-content-left-enter-from {
  opacity: 0;
  transform: translateX(40px);
}

.slide-content-left-leave-to {
  opacity: 0;
  transform: translateX(-40px);
}

.slide-content-right-enter-from {
  opacity: 0;
  transform: translateX(-40px);
}

.slide-content-right-leave-to {
  opacity: 0;
  transform: translateX(40px);
}

@media (prefers-reduced-motion: reduce) {
  .step-content-wrapper {
    transition: height 0.01ms !important;
  }

  .slide-content-left-enter-active,
  .slide-content-left-leave-active,
  .slide-content-right-enter-active,
  .slide-content-right-leave-active {
    transition: opacity 0.15s ease !important;
  }

  .slide-content-left-enter-from,
  .slide-content-left-leave-to,
  .slide-content-right-enter-from,
  .slide-content-right-leave-to {
    transform: none !important;
  }
}
</style>
