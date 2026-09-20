<template>
  <div class="navbar">
    <div class="navbar-left">
      <img class="sys-logo" :src="logoSrc" alt="logo" />
      <span class="sys-main">合规智能助手</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import defaultLogo from '@/assets/images/logo.svg'

const logoSrc = computed(() => defaultLogo)
</script>

<style lang="scss" scoped>
.navbar {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 48px;
  border-bottom: 1px solid $border-color;

  .navbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    font-family: PingFang SC;
    color: #000000;
    font-size: 16px;
    font-weight: 600;

    .sys-logo {
      height: 26px;
      margin-right: 10px;
    }

    .sys-main {
      white-space: nowrap;
    }
  }
}
</style>
