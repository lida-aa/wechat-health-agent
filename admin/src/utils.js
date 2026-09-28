export function resolveMsgType(m) {
  const t = m && m.msgType
  if (t && ['text', 'image', 'file', 'voice'].includes(t)) return t
  return detectMsgType(m && m.content)
}

export function detectMsgType(content) {
  if (!content) return 'text'
  if (/!\[.*\]\(.+\)/.test(content)) return 'image'
  if (/\.(png|jpg|jpeg|gif|webp|svg)(\?|$)/i.test(content)) return 'image'
  if (/\[.+\]\((?!https?:\/\/)[^)]+\)/.test(content)) return 'file'
  return 'text'
}

export function pruneSelection(idSet, items) {
  const validIds = new Set(items.map(it => it.id))
  for (const id of idSet) if (!validIds.has(id)) idSet.delete(id)
}

export async function doBatchDelete(url, ids, token) {
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
      body: JSON.stringify({ ids })
    })
    if (res.status === 404) {
      alert('Batch delete endpoint not found (404). Restart the backend.')
      return false
    }
    if (!res.ok) {
      alert('Delete failed: ' + res.status)
      return false
    }
    const json = await res.json()
    if (json.code !== 200) {
      alert(json.msg || 'Delete failed')
      return false
    }
    return true
  } catch (e) {
    alert('Delete failed: ' + (e.message || 'Network error'))
    return false
  }
}

export function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const pad = n => String(n).padStart(2, '0')
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  } catch (e) {
    return String(t).substring(0, 16)
  }
}

export function renderMarkdown(content) {
  if (!content) return ''
  try {
    return marked.parse(content, { gfm: true, breaks: true })
  } catch (e) {
    return content.replace(/</g, '&lt;').replace(/\n/g, '<br>')
  }
}
