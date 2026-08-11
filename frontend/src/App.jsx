import { useState } from 'react'
import { useAuth } from './auth/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { BlocksPage } from './blocks/BlocksPage'
import { ApplicationsPage } from './applications/ApplicationsPage'
import { DashboardPage } from './dashboard/DashboardPage'
import { ResumesPage } from './resumes/ResumesPage'
import { ResumeFilesPage } from './parsing/ResumeFilesPage'
import { PresetsPage } from './answers/PresetsPage'
import { ProjectsPage } from './portfolio/ProjectsPage'
import { SelectionPage } from './selection/SelectionPage'

const TABS = [
  { key: 'dashboard', label: '今天' },
  { key: 'applications', label: '投遞' },
  { key: 'match', label: '配對 JD' },
  { key: 'resumes', label: '履歷' },
  { key: 'presets', label: '題組' },
  { key: 'projects', label: '作品集' },
  { key: 'blocks', label: '積木' },
  { key: 'upload', label: '匯入' },
]

export function App() {
  const { user, loading, logout } = useAuth()
  const [tab, setTab] = useState('dashboard')

  // 還在確認登入狀態時不要先閃一下登入頁——重整時會很刺眼
  if (loading) return <div className="boot">載入中…</div>
  if (!user) return <LoginPage />

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">Career Toolkit</div>

        <nav className="nav">
          {TABS.map((t) => (
            <button
              key={t.key}
              className={'nav-tab' + (tab === t.key ? ' nav-tab-active' : '')}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </nav>

        <div className="topbar-right">
          <span className="whoami">{user.email}</span>
          {user.role === 'ADMIN' && <span className="badge badge-info">管理員</span>}
          <button className="btn btn-ghost" onClick={logout}>
            登出
          </button>
        </div>
      </header>

      <main className="main">
        {tab === 'dashboard' && <DashboardPage onOpenApplications={() => setTab('applications')} />}
        {tab === 'applications' && <ApplicationsPage />}
        {tab === 'resumes' && <ResumesPage />}
        {tab === 'match' && <SelectionPage />}
        {tab === 'presets' && <PresetsPage />}
        {tab === 'projects' && <ProjectsPage />}
        {tab === 'blocks' && <BlocksPage />}
        {tab === 'upload' && <ResumeFilesPage />}
      </main>
    </div>
  )
}
