import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ChatPage } from './components/chat/ChatPage'
import { HistoryPage } from './components/history/HistoryPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/chat/:conversationId" element={<ChatPage />} />
        <Route path="/history" element={<HistoryPage />} />
      </Routes>
    </BrowserRouter>
  )
}
