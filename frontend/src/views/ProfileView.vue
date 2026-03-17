<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

onMounted(() => {
  if (!userStore.user) userStore.fetchUser()
})
</script>

<template>
  <div class="profile-page">
    <div class="profile-card">
      <h2>个人中心</h2>
      <p v-if="userStore.user" class="profile-username">{{ userStore.user.username }}</p>
      <p class="profile-hint">个人中心功能开发中...</p>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 32px;
  max-width: 600px;
  margin: 0 auto;
}

.profile-card {
  background: var(--bg-card, #fff);
  border: 1px solid var(--border, rgba(0, 0, 0, 0.06));
  border-radius: 16px;
  padding: 32px;
}

.profile-card h2 {
  margin: 0 0 16px;
  font-size: 1.25rem;
  color: var(--text, #111827);
}

.profile-username {
  margin: 0 0 8px;
  font-size: 1rem;
  color: var(--text-secondary, #6b7280);
}

.profile-hint {
  margin: 0;
  font-size: 0.875rem;
  color: var(--text-secondary, #6b7280);
  opacity: 0.8;
}
</style>
