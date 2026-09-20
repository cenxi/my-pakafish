<template>
  <div class="layout-top">
      <Navbar />
    </div>
  <div class="layout-container">

    <!-- 左侧菜单 -->
    <Sidebar />
    <!-- 右侧内容区域 -->
    <div class="layout-right">
      <!-- 面包屑导航 -->
      <!-- <div class="breadcrumb-container">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item
            v-if="route.meta.grandparent"
            :to="route.meta.grandparent.path ? { path: route.meta.grandparent.path } : undefined"
          >
            {{ route.meta.grandparent.title }}
          </el-breadcrumb-item>
          <el-breadcrumb-item
            v-if="route.meta.parent"
            :to="route.meta.parent.path ? { path: route.meta.parent.path } : undefined"
          >
            {{ route.meta.parent.title }}
          </el-breadcrumb-item>
          <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div> -->

      <!-- 内容区 -->
      <main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
</script>

<style lang="scss" scoped>
.layout-container {
  height: calc(100vh - $header-height);
  display: flex;
}

.layout-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.breadcrumb-container {
  padding: 16px 0 8px 16px;
  background-color: #FAFBFF;
  // border-bottom: 1px solid $border-color;
}

.layout-header {
  height: $header-height;
  flex-shrink: 0;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;
}

.layout-main {
  flex: 1;
  min-height: 0;
  background-color: $bg-color;
  overflow-y: auto;
  background: #fafbff;
  position: relative;
  & > div{
    min-height: 100%;
    padding: 18px 15px;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
  }
  & > div.chat-layout-wrapper {
    padding: 0;
    flex-direction: row;
    height: calc(100vh - $header-height);
    min-height: 0;
    overflow: hidden;
  }
  :deep(.el-card){
    border: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
    .el-card__body {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .card-header{
      border-bottom: 0!important;
      margin-bottom: 0!important;
      padding-bottom: 8!important;
    }
  }
}
</style>
