import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue')
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue')
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue')
    },
    {
      path: '/interview',
      name: 'interview',
      component: () => import('../views/InterviewView.vue')
    },
    {
      path: '/resume',
      name: 'resume',
      component: () => import('../views/ResumeView.vue')
    },
    {
      path: '/knowledge-verify',
      name: 'knowledgeVerify',
      component: () => import('../views/KnowledgeVerifyView.vue')
    },
    {
      path: '/interview/result/:sessionId',
      name: 'interviewResult',
      component: () => import('../views/InterviewResultView.vue')
    },
    {
      path: '/card-nav-test',
      name: 'cardNavTest',
      component: () => import('../component/CardNav/Index.vue')
    }
  ],
})

// Simple route guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('accessToken')
  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
