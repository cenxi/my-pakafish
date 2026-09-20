<template>
  <div class="template-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>页面模板</span>
          <el-button type="primary" size="small">操作按钮</el-button>
        </div>
      </template>
      <div class="card-content">
        <p>这是一个基础页面模板，您可以在此开始开发业务功能。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
// 组件逻辑
</script>

<style lang="scss" scoped>
.template-container {
  padding: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-content {
    color: #606266;
    line-height: 1.6;
  }
}
</style>
