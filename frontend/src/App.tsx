import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ChatPage } from './components/chat/ChatPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/chat/:conversationId" element={<ChatPage />} />
      </Routes>
    </BrowserRouter>
  )
}
