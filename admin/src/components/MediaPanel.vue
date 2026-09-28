<script>
export default {
  inject: ['app'],
  methods: {
    kbPick() { this.$refs.kbFileInput?.click() },
    onKbFilePicked(e) {
      const f = e.target.files[0]
      if (f) this.app.kbUpload(f)
      e.target.value = ''
    }
  }
}
</script>

<template>
  <div v-if="app.selectedId && app.activeTab==='kb'" class="content-card">
    <div>
      <div class="drop-zone" @click="kbPick" @dragover.prevent @drop.prevent="e=>app.kbDrop(e, app.selectedId||'')" style="margin-bottom:16px">
        <div class="drop-icon">&#x1F4C1;</div>
        <div class="drop-title">上传经过审核的健康知识资料</div>
        <div style="font-size:12px;color:var(--text-muted);margin-top:4px">最大 5MB，支持 PDF、DOCX、XLSX、TXT、MD、CSV</div>
        <input type="file" ref="kbFileInput" style="display:none" @change="onKbFilePicked" accept=".pdf,.docx,.xlsx,.txt,.md,.csv" />
      </div>
      <div v-if="app.kbDocs.length" class="file-list">
        <div v-for="(d,i) in app.kbDocs" :key="i" class="file-item">
          <div class="file-info">
            <div class="file-name">{{ d.doc_name }}</div>
            <div class="file-preview">{{ d.chunks }} 块 · {{ d.total_chars||0 }} 字</div>
          </div>
          <button @click.stop="app.kbPreview(d.doc_name)" class="btn btn-outline" style="padding:4px 10px;font-size:12px;margin-right:6px">预览</button>
          <button @click="app.kbDelete(d.doc_name)" class="btn btn-danger-outline" style="padding:4px 10px;font-size:12px">删除</button>
        </div>
      </div>
      <div v-else style="text-align:center;color:var(--text-muted);padding:40px 0">知识库为空</div>
    </div>
  </div>
</template>
