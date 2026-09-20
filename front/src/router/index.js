import { createRouter, createWebHashHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const routes = [
  {
    path: '/',
    redirect: '/chess'
  },
  {
    path: '/chess',
    name: 'ChessGame',
    component: () => import('@/views/template/index.vue'),
    meta: { title: '皮卡鱼·AI象棋特大' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chess'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = to.meta.title ? `${to.meta.title}` : '皮卡鱼·AI象棋特大'
  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
