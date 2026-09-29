import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 300000
})

service.interceptors.request.use(
  (config) => {
    config.headers = config.headers || {}
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const { response } = error
    if (response) {
      ElMessage.error(response.data?.message || '请求失败')
    } else {
      ElMessage.error('网络异常，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default service
