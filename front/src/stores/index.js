import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const device = ref('desktop')

  function setDevice(val) {
    device.value = val
  }

  return { device, setDevice }
})
