import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

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
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/HomeView.vue')
        },
        {
          path: 'resume',
          name: 'resume',
          component: () => import('../views/ResumeView.vue')
        },
        {
          path: 'interview',
          name: 'interview',
          component: () => import('../views/InterviewView.vue')
        },
        {
          path: 'interview/report/:sessionId',
          name: 'interview-report',
          component: () => import('../views/InterviewReportView.vue')
        },
        {
          path: 'forum',
          name: 'forum',
          component: () => import('../views/DiscussionBoard.vue')
        },
        {
          path: 'post/:id',
          name: 'post-detail',
          component: () => import('../components/discussion/PostDetail.vue')
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../views/ProfileView.vue'),
          children: [
            {
              path: '',
              redirect: '/profile/info'
            },
            {
              path: 'info',
              name: 'profile-info',
              component: () => import('../views/profile/ProfileInfoView.vue')
            },
            {
              path: 'security',
              name: 'profile-security',
              component: () => import('../views/profile/ProfileSecurityView.vue')
            },
            {
              path: 'messages',
              name: 'profile-messages',
              component: () => import('../views/profile/ProfileInterviewView.vue')
            },
            {
              path: 'binding',
              name: 'profile-binding',
              component: () => import('../views/profile/ProfileShortcutView.vue')
            },
            {
              path: 'resume',
              name: 'profile-resume',
              component: () => import('../views/profile/ProfileResumeView.vue')
            },
            {
              path: 'interview',
              redirect: '/profile/messages'
            },
            {
              path: 'shortcut',
              redirect: '/profile/binding'
            }
          ]
        }
      ]
    }
  ],
})

export default router
