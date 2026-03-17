<template>
  <div class="card-nav-test-page">
    <CardNav
      :logo="logo"
      logoAlt="Company Logo"
      :items="items"
      baseColor="#fff"
      menuColor="#000"
      buttonBgColor="#111"
      buttonTextColor="#fff"
      ease="power3.out"
    />
    <!-- Tailwind 测试：若看到绿色条+白字说明 Tailwind 已生效 -->
    <div
      class="fixed bottom-4 left-1/2 -translate-x-1/2 bg-emerald-500 text-white px-4 py-2 rounded-lg text-sm font-medium shadow-lg"
    >
      ✓ Tailwind 已生效
    </div>
  </div>
</template>

<style scoped>
.card-nav-test-page {
  position: relative;
  min-height: 100vh;
  background: #f5f5f5;
}
</style>

<script setup lang="ts">
import CardNav from './CardNav.vue'
import logo from './logo.svg'

const items = [
  {
    label: "About",
    bgColor: "#0D0716",
    textColor: "#fff",
    links: [
      { label: "Company", ariaLabel: "About Company" },
      { label: "Careers", ariaLabel: "About Careers" }
    ]
  },
  {
    label: "Projects",
    bgColor: "#170D27",
    textColor: "#fff",
    links: [
      { label: "Featured", ariaLabel: "Featured Projects" },
      { label: "Case Studies", ariaLabel: "Project Case Studies" }
    ]
  },
  {
    label: "Contact",
    bgColor: "#271E37",
    textColor: "#fff",
    links: [
      { label: "Email", ariaLabel: "Email us" },
      { label: "Twitter", ariaLabel: "Twitter" },
      { label: "LinkedIn", ariaLabel: "LinkedIn" }
    ]
  }
]
</script>
