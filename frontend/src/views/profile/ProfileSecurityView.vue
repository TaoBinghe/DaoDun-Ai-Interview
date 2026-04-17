<template>
  <el-card shadow="never" class="theme-el-card !rounded-2xl">
    <h2 class="theme-title text-xl font-semibold">安全设置</h2>
    <p class="mt-1 text-sm theme-text-muted">管理登录密码与账号安全相关选项（后续可对接修改密码、二次验证等接口）。</p>

    <div class="mt-6 space-y-4">
      <div class="security-box rounded-xl p-4">
        <p class="text-sm font-medium theme-title">登录密码</p>
        <p class="mt-1 text-xs theme-text-faint">定期更换密码有助于保护账号安全。</p>
        <el-button class="theme-el-btn-primary mt-3 rounded-xl! border-0!" type="primary" disabled>
          修改密码（即将开放）
        </el-button>
      </div>
      <div class="security-box rounded-xl p-4">
        <p class="text-sm font-medium theme-title">登录设备与会话</p>
        <p class="mt-1 text-xs theme-text-faint">查看近期登录记录与安全提醒。</p>
        <el-button class="theme-el-btn-secondary mt-3 rounded-xl!" disabled>
          查看会话（即将开放）
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.security-box {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
}
</style>
