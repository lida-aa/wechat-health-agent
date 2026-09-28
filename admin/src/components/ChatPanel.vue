<script>
import { renderMarkdown } from '../utils.js'

export default {
  inject: ['app'],
  methods: {
    md(content) { return renderMarkdown(content) },
    onTab(t) { this.app.activeTab = t.key },
    scrollToBottom() { const el = this.$refs.chatContainer; if (el) el.scrollTop = el.scrollHeight }
  },
  watch: {
    'app.activeTab'(val) {
      this.app.exitSelectMode()
      this.$nextTick(() => { if (val === 'chat') this.scrollToBottom() })
    },
    'app.currentMessages.length'() { this.$nextTick(() => this.scrollToBottom()) }
  }
}
</script>

<template>
  <div v-if="app.selectedId" :class="['main-panel-inner', app.activeTab==='chat' ? 'chat-active' : '']">
    <div class="panel-topbar">
      <div style="display:flex;align-items:center;gap:12px;min-width:0">
        <div class="mini-logo" style="width:32px;height:32px;border-radius:10px">M</div>
        <div class="topbar-brand">
          <span v-if="app.selectedId" class="user-title" @click="app.startRename">{{ app.currentUser?.username || app.selectedId }}</span>
          <span v-else style="color:var(--text-muted);font-size:14px">未选择会话</span>
          <span v-if="app.selectedId" class="status-dot"></span>
        </div>
      </div>
      <div class="topbar-actions" v-if="app.selectedId && app.role==='admin'">
        <span class="topbar-model">Health Agent · 管理后台</span>
        <button @click="app.clearMessages" class="btn btn-outline">清空对话</button>
        <button @click="app.deleteUser" class="btn btn-danger-outline">删除用户</button>
      </div>
      <div class="topbar-actions" v-else-if="!app.selectedId">
        <span class="topbar-model">Health Agent · 管理后台</span>
      </div>
    </div>
    <div class="tab-bar">
      <span v-for="t in app.tabs" :key="t.key" @click="onTab(t)" :class="['tab-item',app.activeTab===t.key?'active':'']">{{ t.label }}</span>
    </div>
    <div v-if="app.activeTab==='chat'" class="panel-content">
      <div class="chat-view">
        <div class="chat-toolbar">
          <button v-if="app.currentMessages.length>0" @click="app.toggleSelectMode" :class="['btn',app.isMsgSelectMode?'btn-primary':'btn-outline']" style="font-weight:600">{{ app.isMsgSelectMode?'退出选择':'批量选择' }}</button>
          <span v-else style="font-size:12px;color:var(--text-muted)">{{ app.currentMessages.length }} 条消息</span>
          <div class="batch-bar" v-if="app.isMsgSelectMode">
            <span class="cb" :class="{checked:app.isAllMsgSelected}" @click.stop="app.toggleAllMsg">&#10003;</span>
            <span class="batch-count">全选 · 已选 <strong>{{ app.selectedMsgIds.size }}</strong>/{{ app.currentMessages.length }}</span>
            <button v-if="app.anyMsgSelected" @click="app.batchDeleteMsg" class="btn btn-danger-outline" style="padding:5px 10px;font-size:12px">删除选中({{ app.selectedMsgIds.size }})</button>
          </div>
        </div>
        <div class="chat-stream" ref="chatContainer">
          <div class="chat-stream-inner">
            <div v-for="(m,i) in app.currentMessages" :key="m.id||i" :class="['chat-row',m.role]">
              <span v-if="app.isMsgSelectMode" class="cb" style="margin-right:8px;align-self:center" :class="{checked:app.selectedMsgIds.has(m.id)}" @click.stop="app.toggleMsgSelect(m.id)">&#10003;</span>
              <div :class="['msg-avatar',m.role]">{{ m.role==='assistant' ? 'M' : app.avatarChar() }}</div>
              <div class="msg-wrap">
                <div :class="['msg-bubble',m.role]">
                  <div v-html="md(m.content)"></div>
                </div>
                <div class="msg-time">{{ app.formatTime(m.time) }}</div>
              </div>
            </div>
            <div v-if="!app.currentMessages.length" style="text-align:center;color:var(--text-muted);padding:40px 0">暂无对话记录</div>
          </div>
        </div>
        <div class="chat-composer">
          <div class="composer-box">
            <textarea class="composer-input" v-model="app.composerInput" rows="1" placeholder="与 Agent 对话…（当前为只读视图）" @keydown.enter.prevent="app.composerSend"></textarea>
            <button class="composer-send" @click="app.composerSend" title="发送">&#8594;</button>
          </div>
          <div class="composer-hint">{{ app.composerHint || '消息经微信实时同步 · 每 5 秒自动刷新' }}</div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="empty-state">
    <div class="empty-icon">&#x1F464;</div>
    <div class="empty-text">未选择会话</div>
    <div class="empty-hint">从左侧选择一个会话查看对话记录</div>
  </div>
</template>
