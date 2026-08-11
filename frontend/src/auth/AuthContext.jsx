import { createContext, useContext, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // 開場先問一次「我還在登入狀態嗎」。
    //
    // 這一次呼叫還有一個副作用是必要的：後端的 CsrfCookieFilter 會在回應裡
    // 寫出 XSRF-TOKEN cookie。沒有先發過任何請求的話，第一個 POST 會因為
    // 手上沒有 token 而被擋成 403。就算這裡回 401 也一樣會拿到 cookie。
    api
      .me()
      .then(setUser)
      .catch((err) => {
        if (!(err instanceof ApiError && err.status === 401)) {
          console.error('確認登入狀態時發生非預期錯誤', err)
        }
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const value = {
    user,
    loading,
    login: async (email, password) => setUser(await api.login(email, password)),
    register: async (email, password, displayName) => {
      await api.register(email, password, displayName)
      setUser(await api.login(email, password))
    },
    logout: async () => {
      await api.logout()
      setUser(null)
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth 必須放在 AuthProvider 裡面')
  return context
}
