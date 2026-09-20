<template>
  <div class="sidebar" :class="{ collapsed: isCollapsed }">
    <!-- 菜单列表 -->
    <div class="sidebar-menu">
      <template v-for="item in menuItems" :key="item.path">
        <div
          class="menu-item"
          :class="{ active: isActive(item.path) }"
          @click="handleClick(item.path)"
        >
          <el-icon :size="18">
            <component :is="item.icon" />
          </el-icon>
          <span v-if="!isCollapsed" class="menu-title">{{ item.title }}</span>
        </div>
      </template>
    </div>

    <!-- 折叠按钮 -->
    <div class="collapse-btn" @click="toggleCollapse">
      <el-icon>
        <Fold v-if="!isCollapsed" />
        <Expand v-else />
      </el-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)

const menuItems = [
  { path: '/template', title: '模板页面', icon: 'Document' }
]

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')

function handleClick(path) {
  router.push(path)
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style lang="scss" scoped>
.sidebar {
  width: $sidebar-width;
  height: 100%;
  border-right: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;
  background: #fff;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.sidebar-menu {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  color: $text-regular;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;

  &:hover {
    background: #f2f3f5;
    color: $text-primary;
  }

  &.active {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
    font-weight: 500;
  }

  .menu-title {
    font-size: 14px;
  }
}

.collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bfcbd9;
  cursor: pointer;
  border-top: 1px solid $border-color;
  transition: all 0.3s;

  &:hover {
    background-color: #f2f3f5;
    color: $text-primary;
  }

  .el-icon {
    font-size: 18px;
  }
}
</style>
