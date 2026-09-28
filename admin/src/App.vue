<script>
import Sidebar from './components/Sidebar.vue'
import ChatPanel from './components/ChatPanel.vue'
import MediaPanel from './components/MediaPanel.vue'
import ShowcasePage from './components/ShowcasePage.vue'
import { resolveMsgType, pruneSelection, doBatchDelete, formatTime as fmtFn } from './utils.js'

const STORAGE_KEY = 'health_agent_users'

export default {
  components: { Sidebar, ChatPanel, MediaPanel, ShowcasePage },
  provide() { return { app: this } },
  data() {
    return {
      users: [],
      selectedId: '',
      activeTab: 'chat',
      token: '',
      role: '',
      loginUser: 'admin',
      loginPass: '',
      loginError: '',
      showLoginModal: false,
      tabs: [{ key: 'chat', label: '健康会话' }, { key: 'kb', label: '健康知识库' }],
      selectedMsgIds: new Set(),
      isMsgSelectMode: false,
      kbDocs: [],
      kbUploading: false,
      composerInput: '',
      composerHint: '',
    }
  },
  computed: {
    currentUser() { return this.users.find(user => user.userId === this.selectedId) },
    currentMessages() { return this.currentUser?.messages || [] },
    isAllMsgSelected() {
      const ids = this.currentMessages.map(message => message.id)
      return ids.length > 0 && ids.every(id => this.selectedMsgIds.has(id))
    },
    anyMsgSelected() { return this.selectedMsgIds.size > 0 },
  },
  methods: {
    async doLogin() {
      this.loginError = ''
      try {
        const response = await fetch('/api/admin/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: this.loginUser, password: this.loginPass }),
        })
        const result = await response.json()
        if (result.code !== 200) {
          this.loginError = result.msg || '登录失败'
          return
        }
        this.token = result.token
        this.role = 'admin'
        this.showLoginModal = false
        localStorage.setItem('agent_token', result.token)
        localStorage.setItem('agent_role', 'admin')
        await this.fetchFromBackend()
        this.startPolling()
      } catch (error) {
        this.loginError = '后台服务暂不可用'
      }
    },
    openAdminLogin() {
      this.loginUser = 'admin'
      this.loginPass = ''
      this.loginError = ''
      this.showLoginModal = true
    },
    loadFromStorage() {
      try {
        const cached = localStorage.getItem(STORAGE_KEY)
        if (cached) this.users = JSON.parse(cached)
      } catch (error) {
        this.users = []
      }
      this.fetchFromBackend()
    },
    async fetchFromBackend() {
      if (!this.token) return
      try {
        const response = await fetch('/api/admin/users', {
          headers: { Authorization: 'Bearer ' + this.token },
        })
        const result = await response.json()
        if (result.code === 403) {
          this.logout()
          return
        }
        if (result.code !== 200 || !result.data) return
        this.users = result.data.map(user => {
          const existing = this.users.find(item => item.userId === user.fromUserId)
          return {
            userId: user.fromUserId,
            username: user.nickname || existing?.username || '',
            createTime: user.firstMsgTime || existing?.createTime || new Date().toISOString(),
            messages: existing?.messages || [],
          }
        })
        if (!this.selectedId || !this.users.some(user => user.userId === this.selectedId)) {
          this.selectedId = this.users[0]?.userId || ''
        }
        if (this.selectedId) await this.fetchUserMessages(this.selectedId)
        await this.fetchKbDocs()
        this.saveToStorage()
      } catch (error) {
        // Keep the last local snapshot during a temporary backend outage.
      }
    },
    saveToStorage() { localStorage.setItem(STORAGE_KEY, JSON.stringify(this.users)) },
    logout() {
      this.stopPolling()
      localStorage.removeItem('agent_token')
      localStorage.removeItem('agent_role')
      this.token = ''
      this.role = ''
      this.selectedId = ''
    },
    async selectUser(userId) {
      this.selectedId = userId
      this.activeTab = 'chat'
      this.exitSelectMode()
      await this.fetchUserMessages(userId)
      await this.fetchKbDocs()
    },
    async fetchUserMessages(userId) {
      try {
        const response = await fetch(`/api/admin/users/${encodeURIComponent(userId)}/messages?page=0`, {
          headers: { Authorization: 'Bearer ' + this.token },
        })
        const result = await response.json()
        if (result.code !== 200) return
        const user = this.users.find(item => item.userId === userId)
        if (!user) return
        const messages = (result.data || []).map(message => ({
          role: message.role,
          content: message.content,
          time: message.createdAt,
          type: resolveMsgType(message),
          id: message.id || `${message.role}-${message.createdAt}`,
        })).reverse()
        user.messages = messages
        pruneSelection(this.selectedMsgIds, this.currentMessages)
        this.saveToStorage()
      } catch (error) {
        // Polling will retry.
      }
    },
    async clearMessages() {
      if (!this.currentUser || !confirm('确定清空该用户的对话吗？')) return
      await fetch(`/api/admin/users/${encodeURIComponent(this.selectedId)}/messages`, {
        method: 'DELETE',
        headers: { Authorization: 'Bearer ' + this.token },
      })
      this.currentUser.messages = []
      this.exitSelectMode()
      this.saveToStorage()
    },
    async deleteUser() {
      if (!this.currentUser || !confirm('确定删除该用户及其后台数据吗？')) return
      await fetch(`/api/admin/users/${encodeURIComponent(this.selectedId)}`, {
        method: 'DELETE',
        headers: { Authorization: 'Bearer ' + this.token },
      })
      this.users = this.users.filter(user => user.userId !== this.selectedId)
      this.selectedId = this.users[0]?.userId || ''
      this.exitSelectMode()
      this.saveToStorage()
    },
    async startRename() {
      const nickname = prompt('输入新的显示名称', this.currentUser?.username || '')
      if (!nickname?.trim()) return
      await fetch(`/api/admin/users/${encodeURIComponent(this.selectedId)}/nickname`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + this.token },
        body: JSON.stringify({ nickname: nickname.trim() }),
      })
      this.currentUser.username = nickname.trim()
      this.saveToStorage()
    },
    toggleSelectMode() {
      this.isMsgSelectMode = !this.isMsgSelectMode
      if (!this.isMsgSelectMode) this.selectedMsgIds = new Set()
    },
    exitSelectMode() {
      this.isMsgSelectMode = false
      this.selectedMsgIds = new Set()
    },
    toggleMsgSelect(id) {
      const selected = new Set(this.selectedMsgIds)
      selected.has(id) ? selected.delete(id) : selected.add(id)
      this.selectedMsgIds = selected
    },
    toggleAllMsg() {
      this.selectedMsgIds = this.isAllMsgSelected
        ? new Set()
        : new Set(this.currentMessages.map(message => message.id))
    },
    async batchDeleteMsg() {
      const ids = Array.from(this.selectedMsgIds)
      if (!ids.length || !confirm(`删除选中的 ${ids.length} 条消息吗？`)) return
      const ok = await doBatchDelete(
        `/api/admin/users/${encodeURIComponent(this.selectedId)}/messages/batch`, ids, this.token)
      if (!ok) return
      const selected = new Set(ids)
      this.currentUser.messages = this.currentUser.messages.filter(message => !selected.has(message.id))
      this.exitSelectMode()
      this.saveToStorage()
    },
    async fetchKbDocs() {
      if (!this.token) return
      const query = this.selectedId ? `?userId=${encodeURIComponent(this.selectedId)}` : ''
      try {
        const response = await fetch('/api/admin/knowledge/list' + query, {
          headers: { Authorization: 'Bearer ' + this.token },
        })
        const result = await response.json()
        if (result.code === 200) this.kbDocs = result.data || []
      } catch (error) {
        this.kbDocs = []
      }
    },
    kbDrop(event) {
      event.preventDefault()
      const file = event.dataTransfer.files[0]
      if (file) this.kbUpload(file)
    },
    async kbUpload(file) {
      this.kbUploading = true
      try {
        const form = new FormData()
        form.append('file', file)
        if (this.selectedId) form.append('userId', this.selectedId)
        const response = await fetch('/api/admin/knowledge/upload', {
          method: 'POST',
          headers: { Authorization: 'Bearer ' + this.token },
          body: form,
        })
        const result = await response.json()
        if (result.code !== 200) {
          alert(result.msg || '上传失败')
          return false
        }
        await this.fetchKbDocs()
        return true
      } catch (error) {
        alert('上传失败')
        return false
      } finally {
        this.kbUploading = false
      }
    },
    async kbDelete(docName) {
      if (!confirm(`删除健康知识文档 ${docName} 吗？`)) return
      const query = this.selectedId ? `?userId=${encodeURIComponent(this.selectedId)}` : ''
      await fetch(`/api/admin/knowledge/${encodeURIComponent(docName)}${query}`, {
        method: 'DELETE',
        headers: { Authorization: 'Bearer ' + this.token },
      })
      await this.fetchKbDocs()
    },
    async kbPreview(docName) {
      try {
        const response = await fetch(`/api/admin/knowledge/preview/${encodeURIComponent(docName)}`, {
          headers: { Authorization: 'Bearer ' + this.token },
        })
        const result = await response.json()
        if (result.code !== 200) return
        const overlay = document.getElementById('filePreviewOverlay')
        if (!overlay) return
        document.getElementById('filePreviewName').textContent = result.data.name
        document.getElementById('filePreviewContent').textContent = result.data.content
        document.getElementById('filePreviewContent').style.display = 'block'
        overlay.style.display = 'flex'
        overlay.onclick = event => { if (event.target === overlay) overlay.style.display = 'none' }
        overlay.querySelector('.modal-close').onclick = () => { overlay.style.display = 'none' }
      } catch (error) {
        alert('预览失败')
      }
    },
    avatarChar() {
      const name = this.currentUser?.username || this.selectedId || '?'
      return (name.trim().charAt(0) || '?').toUpperCase()
    },
    formatTime(time) { return fmtFn(time) },
    composerSend() {
      if ((this.composerInput || '').trim()) this.composerInput = ''
      this.composerHint = '后台为只读视图，请通过微信与健康助手对话'
      clearTimeout(this._composerTimer)
      this._composerTimer = setTimeout(() => { this.composerHint = '' }, 4000)
    },
    startPolling() {
      this.stopPolling()
      this._timer = setInterval(() => this.fetchFromBackend(), 5000)
    },
    stopPolling() {
      if (this._timer) clearInterval(this._timer)
      this._timer = null
    },
  },
  watch: {
    selectedId() { this.exitSelectMode() },
  },
  beforeUnmount() { this.stopPolling() },
  mounted() {
    this.token = localStorage.getItem('agent_token') || ''
    this.role = localStorage.getItem('agent_role') || ''
    if (!this.token || this.role !== 'admin') return
    this.loadFromStorage()
    this.startPolling()
  },
}
</script>

<template>
  <ShowcasePage v-if="!token" />
  <div v-else class="layout">
    <Sidebar />
    <div class="main-panel">
      <ChatPanel />
      <MediaPanel />
    </div>
  </div>
</template>
