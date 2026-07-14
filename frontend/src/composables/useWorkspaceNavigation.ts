import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  isWorkspaceModule,
  WORKSPACE_MODULE_META,
  type WorkspaceModule,
} from '@/constants/workbench'

function moduleFromLocation(): WorkspaceModule {
  const moduleName = window.location.hash.replace(/^#\/?/, '')
  return isWorkspaceModule(moduleName) ? moduleName : 'overview'
}

export function useWorkspaceNavigation() {
  const activeModule = ref<WorkspaceModule>('overview')
  const activeModuleMeta = computed(() => WORKSPACE_MODULE_META[activeModule.value])

  function syncModuleFromLocation(): void {
    activeModule.value = moduleFromLocation()
  }

  function setActiveModule(moduleName: WorkspaceModule): void {
    activeModule.value = moduleName
    const targetHash = `#${moduleName}`
    if (window.location.hash !== targetHash) {
      window.history.pushState(null, '', targetHash)
    }
  }

  onMounted(() => {
    syncModuleFromLocation()
    window.addEventListener('hashchange', syncModuleFromLocation)
    window.addEventListener('popstate', syncModuleFromLocation)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('hashchange', syncModuleFromLocation)
    window.removeEventListener('popstate', syncModuleFromLocation)
  })

  return {
    activeModule,
    activeModuleMeta,
    setActiveModule,
  }
}
