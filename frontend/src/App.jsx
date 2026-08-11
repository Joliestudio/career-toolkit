import { useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { BlocksPage } from './blocks/BlocksPage'

export function App() {
  const { user, loading } = useAuth()

  // 還在確認登入狀態時不要先閃一下登入頁——重整時會很刺眼
  if (loading) return <div className="boot">載入中…</div>

  return user ? <BlocksPage /> : <LoginPage />
}
