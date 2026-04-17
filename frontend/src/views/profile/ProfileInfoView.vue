<template>
  <el-card shadow="never" class="theme-el-card !rounded-2xl">
    <div class="mb-6 flex items-center justify-between gap-4">
      <h2 class="theme-title text-xl font-semibold">账户信息与安全</h2>
      <div class="flex items-center gap-2">
        <el-button
          v-if="!isEditingProfile"
          class="theme-el-btn-secondary h-9! rounded-xl! px-4!"
          @click="startEditProfile"
        >
          修改资料
        </el-button>
        <template v-else>
          <el-button class="theme-el-btn-secondary h-9! rounded-xl! px-4!" @click="cancelEditProfile">取消</el-button>
          <el-button class="theme-el-btn-primary h-9! rounded-xl! border-0! px-4!" @click="saveProfile">保存</el-button>
        </template>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[200px,1fr]">
      <div class="flex flex-col items-center justify-start lg:justify-center">
        <div class="profile-avatar flex h-24 w-24 items-center justify-center rounded-full text-3xl font-semibold">
          {{ avatarText }}
        </div>
      </div>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div class="profile-box rounded-xl p-4">
          <p class="text-xs theme-text-faint">用户ID</p>
          <p class="mt-2 text-sm font-medium theme-title">{{ userStore.user?.userId ?? '-' }}</p>
        </div>
        <div class="profile-box rounded-xl p-4">
          <p class="text-xs theme-text-faint">邮箱</p>
          <p class="mt-2 truncate text-sm font-medium theme-title">{{ userStore.user?.email ?? '-' }}</p>
        </div>
        <div class="profile-box rounded-xl p-4">
          <p class="text-xs theme-text-faint">用户名</p>
          <div class="mt-2">
            <el-input v-if="isEditingProfile" v-model="profileForm.username" placeholder="请输入用户名" />
            <p v-else class="text-sm font-medium theme-title">{{ profileForm.username || '-' }}</p>
          </div>
        </div>
        <div class="profile-box rounded-xl p-4">
          <p class="text-xs theme-text-faint">性别</p>
          <div class="mt-2">
            <el-select v-if="isEditingProfile" v-model="profileForm.gender" placeholder="请选择性别" class="w-full">
              <el-option label="男" value="male" />
              <el-option label="女" value="female" />
              <el-option label="其他" value="other" />
            </el-select>
            <p v-else class="text-sm font-medium theme-title">{{ genderLabel(profileForm.gender) }}</p>
          </div>
        </div>
        <div class="profile-box rounded-xl p-4 md:col-span-2">
          <p class="text-xs theme-text-faint">出生日期</p>
          <div class="mt-2">
            <el-date-picker
              v-if="isEditingProfile"
              v-model="profileForm.birthDate"
              type="date"
              placeholder="选择出生日期"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
            <p v-else class="text-sm font-medium theme-title">{{ profileForm.birthDate || '-' }}</p>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.profile-avatar {
  background: var(--app-surface-strong);
  color: color-mix(in srgb, var(--app-text) 80%, transparent);
  border: 1px solid var(--app-border);
}

.profile-box {
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const isEditingProfile = ref(false)
const profileForm = ref({ username: '', gender: '', birthDate: '' })
const profileSnapshot = ref({ username: '', gender: '', birthDate: '' })
const hardcodedProfileDefaults = {
  gender: 'male',
  birthDate: '1999-01-01'
}

const avatarText = computed(() => {
  const name = profileForm.value.username || userStore.user?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

function genderLabel(gender: string) {
  if (gender === 'male') return '男'
  if (gender === 'female') return '女'
  if (gender === 'other') return '其他'
  return '-'
}

function seedProfileForm() {
  profileForm.value.username = userStore.user?.username || ''
  profileForm.value.gender = hardcodedProfileDefaults.gender
  profileForm.value.birthDate = hardcodedProfileDefaults.birthDate
  profileSnapshot.value = { ...profileForm.value }
}

function startEditProfile() {
  isEditingProfile.value = true
}

function cancelEditProfile() {
  profileForm.value = { ...profileSnapshot.value }
  isEditingProfile.value = false
}

function saveProfile() {
  if (!profileForm.value.username.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  userStore.setUser({
    userId: userStore.user?.userId ?? 0,
    email: userStore.user?.email ?? '',
    username: profileForm.value.username.trim()
  })
  profileSnapshot.value = { ...profileForm.value }
  isEditingProfile.value = false
  ElMessage.success('修改成功')
}

onMounted(async () => {
  if (!userStore.user) {
    await userStore.fetchUser()
  }
  seedProfileForm()
})
</script>
