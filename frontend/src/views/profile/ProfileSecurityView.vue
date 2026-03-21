<template>
  <el-card shadow="never" class="!rounded-2xl !border-[#ffffff14] !bg-[#1d1e1d]">
    <h2 class="text-xl font-semibold text-[#f1f1ec]">安全设置</h2>
    <p class="mt-1 text-sm text-[#9f9f99]">管理登录密码与账号安全相关选项（后续可对接修改密码、二次验证等接口）。</p>

    <div class="mt-6 space-y-4">
      <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
        <p class="text-sm font-medium text-[#f1f1ec]">登录密码</p>
        <p class="mt-1 text-xs text-[#9f9f99]">定期更换密码有助于保护账号安全。</p>
        <el-button class="mt-3 !rounded-xl !border-0 !bg-[#6ef17d] !text-black hover:!bg-[#5edb6b]" type="primary" disabled>
          修改密码（即将开放）
        </el-button>
      </div>
      <div class="rounded-xl border border-[#ffffff14] bg-[#111211] p-4">
        <p class="text-sm font-medium text-[#f1f1ec]">登录设备与会话</p>
        <p class="mt-1 text-xs text-[#9f9f99]">查看近期登录记录与安全提醒。</p>
        <el-button class="mt-3 !rounded-xl !border-[#ffffff1f] !bg-[#2a2b2a] !text-[#f1f1ec] hover:!bg-[#343533]" disabled>
          查看会话（即将开放）
        </el-button>
      </div>
    </div>
  </el-card>
</template>
