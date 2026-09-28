<script>
import { formatTime } from '../utils.js'

export default {
  inject: ['app'],
  data() {
    return { searchQuery: '' }
  },
  computed: {
    filteredUsers() {
      const app = this.app
      if (!this.searchQuery) return app.users
      const q = this.searchQuery.toLowerCase()
      return app.users.filter(u => (u.username || u.userId).toLowerCase().includes(q))
    }
  },
  methods: {
    fmt(t) { return formatTime(t) },
    latestMsg(user) {
      if (!user.messages?.length) return ''
      const last = user.messages[user.messages.length - 1]
      return (last.content || '').substring(0, 40)
    }
  }
}
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
        <div class="mini-logo">M</div>
        <div>
          <div style="font-size:15px;font-weight:800;letter-spacing:-0.02em;line-height:1.3">Health Agent</div>
          <div style="font-size:11px;color:var(--text-muted)">微信健康助手 · 管理后台</div>
        </div>
      </div>
      <input v-model="searchQuery" placeholder="搜索会话..." class="sidebar-search" />
    </div>
    <div class="sidebar-list">
      <div v-for="u in filteredUsers" :key="u.userId" @click="app.selectUser(u.userId)" :class="['user-card', app.selectedId===u.userId?'selected':'']">
        <div class="session-avatar">{{ (u.username||u.userId).charAt(0).toUpperCase() }}</div>
        <div class="session-main">
          <div class="session-top">
            <span class="user-name">{{ u.username || u.userId }}</span>
            <span class="user-time">{{ fmt(u.messages?.length>0?u.messages[u.messages.length-1].time:u.createTime) }}</span>
          </div>
          <div class="user-preview">{{ latestMsg(u) || '暂无消息' }}</div>
        </div>
      </div>
    </div>
    <div class="sidebar-footer">
      共 <span class="count">{{ app.users.length }}</span> 个用户 · 管理员
      <button @click="app.logout" class="btn btn-outline" style="padding:2px 8px;font-size:11px">退出</button>
    </div>
  </div>
</template>
