import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ChatPage } from './components/chat/ChatPage'
import { DeepResearchPage } from './components/deep-research/DeepResearchPage'
import { FileQaPage } from './components/file-qa/FileQaPage'
import { HistoryPage } from './components/history/HistoryPage'
import { PptGenPage } from './components/ppt/PptGenPage'
import { SkillsPage } from './components/skills/SkillsPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/chat/:conversationId" element={<ChatPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/file-qa" element={<FileQaPage />} />
        <Route path="/ppt" element={<PptGenPage />} />
        <Route path="/ppt/:conversationId" element={<PptGenPage />} />
        <Route path="/deep-research" element={<DeepResearchPage />} />
        <Route path="/skills" element={<SkillsPage />} />
        <Route path="/skills/:conversationId" element={<SkillsPage />} />
      </Routes>
    </BrowserRouter>
  )
}
