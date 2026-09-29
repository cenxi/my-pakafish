import { createRouter, createWebHashHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const isMobileDevice = () => {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
    || window.innerWidth <= 768
}

const routes = [
  {
    path: '/',
    redirect: () => (isMobileDevice() ? '/mobile' : '/chess')
  },
  {
    path: '/chess',
    name: 'ChessGame',
    component: () => import('@/views/template/index.vue'),
    meta: { title: '楚赢象棋 · 特级大师私教' }
  },
  {
    path: '/mobile',
    name: 'ChessMobile',
    component: () => import('@/views/mobile/index.vue'),
    meta: { title: '楚赢象棋 · 手机端' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => (isMobileDevice() ? '/mobile' : '/chess')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title}` : '楚赢象棋'
  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
