<template>
  <div class="w-full space-y-4">
    <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
      <div class="w-full lg:max-w-md">
        <SearchBar
          v-model="keyword"
          :placeholder="$t('dashboard.audit_log.search_placeholder')"
        />
      </div>

      <div class="flex items-center gap-3 w-full lg:w-auto">
        <select
          v-model="actionFilter"
          class="flex-1 lg:flex-none px-4 py-2 bg-white/5 border border-white/10 rounded-lg text-white text-sm focus:outline-none focus:border-purple-500/50 appearance-none cursor-pointer hover:bg-white/10 transition-colors"
        >
          <option value="" class="bg-neutral-900">{{ $t('dashboard.audit_log.all_actions') }}</option>
          <option
            v-for="action in availableActions"
            :key="action"
            :value="action"
            class="bg-neutral-900"
          >
            {{ $t(`dashboard.audit_log.actions.${action}`) }}
          </option>
        </select>
        <Button
          @click="loadAuditLogs"
          :disabled="loading"
          variant="outline"
          class="gap-2"
        >
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
          {{ $t('common.refresh') }}
        </Button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading && !items.length" class="flex flex-col items-center justify-center py-16 text-white/60">
      <RefreshCw class="w-10 h-10 animate-spin text-purple-500 mb-4" />
      <p>{{ $t('common.loading') }}</p>
    </div>

    <!-- Audit Logs Table -->
    <Card v-else class="overflow-hidden">
      <div class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{{ $t('dashboard.audit_log.table.time') }}</TableHead>
              <TableHead>{{ $t('dashboard.audit_log.table.action') }}</TableHead>
              <TableHead>{{ $t('dashboard.audit_log.table.operator') }}</TableHead>
              <TableHead>{{ $t('dashboard.audit_log.table.target') }}</TableHead>
              <TableHead>{{ $t('dashboard.audit_log.table.detail') }}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="log in items" :key="log.id || log.occurredAt">
              <TableCell class="whitespace-nowrap text-white/70">
                {{ formatTime(log.occurredAt) }}
              </TableCell>
              <TableCell>
                <span :class="getActionClass(log.action)" class="inline-block">
                  {{ $t(`dashboard.audit_log.actions.${log.action}`) }}
                </span>
              </TableCell>
              <TableCell class="font-medium text-white">{{ log.operator || 'System' }}</TableCell>
              <TableCell class="font-medium text-white">{{ log.target || '—' }}</TableCell>
              <TableCell class="max-w-xs truncate text-white/70" :title="log.detail || ''">
                {{ log.detail || '—' }}
              </TableCell>
            </TableRow>
            <TableRow v-if="items.length === 0">
              <TableCell colspan="5" class="h-32 text-center">
                <div class="flex flex-col items-center justify-center text-white/40">
                  <FileText class="w-12 h-12 mb-3" />
                  <p>{{ $t('dashboard.audit_log.no_logs') }}</p>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <Pagination
      :current-page="currentPage"
      :total-pages="totalPages"
      :total-count="totalCount"
      :page-size="pageSize"
      :has-next="hasNext"
      :has-prev="hasPrev"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshCw, FileText } from 'lucide-vue-next'
import { useNotification } from '@/composables/useNotification'
import { useAuditLogs } from '@/composables/useAuditLogs'
import Card from '@/components/ui/Card.vue'
import Button from '@/components/ui/Button.vue'
import Table from '@/components/ui/Table.vue'
import TableHeader from '@/components/ui/TableHeader.vue'
import TableBody from '@/components/ui/TableBody.vue'
import TableRow from '@/components/ui/TableRow.vue'
import TableHead from '@/components/ui/TableHead.vue'
import TableCell from '@/components/ui/TableCell.vue'
import Pagination from '@/components/ui/Pagination.vue'
import SearchBar from '@/components/ui/SearchBar.vue'

const { t } = useI18n()
const notification = useNotification()

const {
  loading,
  items,
  availableActions,
  keyword,
  actionFilter,
  currentPage,
  pageSize,
  totalCount,
  totalPages,
  hasNext,
  hasPrev,
  loadAuditLogs,
  handlePageChange,
  handlePageSizeChange,
} = useAuditLogs({ t, notification })

const formatTime = (timestamp: number): string => {
  const date = new Date(timestamp)
  return date.toLocaleString()
}

const getActionClass = (action: string): string => {
  const actionLower = action.toLowerCase()
  const baseClasses = 'px-2 py-1 rounded-full text-xs font-medium'

  switch (actionLower) {
    case 'approve':
      return `${baseClasses} bg-green-500/20 text-green-300`
    case 'reject':
      return `${baseClasses} bg-red-500/20 text-red-300`
    case 'ban':
      return `${baseClasses} bg-red-500/20 text-red-300`
    case 'unban':
      return `${baseClasses} bg-blue-500/20 text-blue-300`
    case 'delete':
      return `${baseClasses} bg-orange-500/20 text-orange-300`
    case 'password_change':
      return `${baseClasses} bg-amber-500/20 text-amber-300`
    case 'password_migration':
      return `${baseClasses} bg-indigo-500/20 text-indigo-300`
    case 'email_update':
      return `${baseClasses} bg-cyan-500/20 text-cyan-300`
    case 'admin_access_denied':
      return `${baseClasses} bg-rose-500/20 text-rose-300`
    default:
      return `${baseClasses} bg-white/10 text-white/70`
  }
}

onMounted(() => {
  loadAuditLogs()
})
</script>
