<script>
export default {
  inject: ['app'],
  data() {
    return {
      demoMsgs: [
        { role: 'user', content: '这是我今天的午餐，帮我记录一下。' },
        { role: 'assistant', content: '我识别到：米饭约 150g、鸡胸肉约 100g、青菜约 120g，估算约 480 千卡。\n\n这是待确认草稿，还没有写入健康档案。需要修改分量或餐别吗？' },
        { role: 'user', content: '午餐，分量没问题，确认记录。' },
        { role: 'assistant', content: '已记录午餐。今天累计摄入约 480 千卡。图片识别和热量均为估算值，仅用于日常健康管理。' },
      ],
    }
  },
}
</script>

<template>
  <div class="showcase-wrap">
    <div class="showcase">
      <nav class="showcase-nav">
        <div class="showcase-brand">
          <div class="mini-logo">H</div>
          <div>
            <div class="brand-name">Health Agent</div>
            <div style="font-size:11px;color:var(--text-muted)">微信健康管理助手</div>
          </div>
        </div>
        <div class="showcase-nav-actions">
          <a href="#features" class="btn btn-outline">了解项目</a>
          <button @click="app.openAdminLogin" class="btn btn-primary">管理员登录</button>
        </div>
      </nav>

      <section class="hero">
        <div class="hero-badge">HEALTH MANAGEMENT AGENT</div>
        <h1>把零散的健康信息<br><span class="brand-serif">整理成可追踪的日常记录</span></h1>
        <p class="hero-sub">通过微信记录饮食、体征和用药情况。模型负责理解自然语言与图片，后端负责权限校验、用户确认、数据保存和定时提醒。</p>
        <div class="hero-actions">
          <a href="#features" class="btn btn-primary btn-lg">查看核心能力</a>
          <button @click="app.openAdminLogin" class="btn btn-outline btn-lg">查看管理后台</button>
        </div>
        <div class="demo-chat">
          <div class="demo-row user">
            <div class="demo-avatar">我</div>
            <div class="demo-bubble">刚量的血压是 135/85，帮我记一下。</div>
          </div>
          <div class="demo-row assistant">
            <div class="demo-avatar assistant">H</div>
            <div class="demo-bubble">记录草稿：血压 135/85 mmHg，测量时间为现在。确认后才会写入你的健康档案。</div>
          </div>
        </div>
      </section>

      <section id="features" class="sec">
        <div class="sec-title">聚焦一个完整健康闭环</div>
        <div class="sec-sub">不是医疗诊断系统，也不是堆叠工具的万能机器人。</div>
        <div class="feature-grid">
          <div class="feature-card"><div class="f-icon">PIC</div><h3>饮食图片识别</h3><p>把食物图片转成结构化草稿，标明分量和热量均为估算值。</p></div>
          <div class="feature-card"><div class="f-icon">HITL</div><h3>写入前确认</h3><p>健康档案、体征、用药和饮食数据只有在用户明确确认后才保存。</p></div>
          <div class="feature-card"><div class="f-icon">DATA</div><h3>个人健康档案</h3><p>按用户隔离基础资料、慢病信息、过敏史、体征和日常记录。</p></div>
          <div class="feature-card"><div class="f-icon">MED</div><h3>用药与提醒</h3><p>记录用药计划和服药打卡，通过定时任务发送健康提醒。</p></div>
          <div class="feature-card"><div class="f-icon">SAFE</div><h3>安全边界</h3><p>不做诊断、不调整处方；紧急症状优先提示急救或及时就医。</p></div>
          <div class="feature-card"><div class="f-icon">EVAL</div><h3>可验证执行</h3><p>工具白名单、调用轨迹和场景测试让 Agent 行为可以复现与评估。</p></div>
        </div>
      </section>

      <section class="sec">
        <div class="sec-title">核心执行链路</div>
        <div class="sec-sub">微信是接入渠道，健康 Agent 才是业务核心。</div>
        <div class="feature-grid">
          <div class="feature-card"><div class="f-icon">1</div><h3>理解</h3><p>接收文字、语音或图片，提取健康意图与结构化字段。</p></div>
          <div class="feature-card"><div class="f-icon">2</div><h3>草稿</h3><p>展示即将保存的内容、估算项和不确定项。</p></div>
          <div class="feature-card"><div class="f-icon">3</div><h3>确认</h3><p>等待用户修改、确认或取消，不让模型直接写入敏感数据。</p></div>
          <div class="feature-card"><div class="f-icon">4</div><h3>执行</h3><p>调用受限健康工具，完成持久化、提醒和结果回执。</p></div>
        </div>
      </section>

      <section id="demos" class="sec">
        <div class="sec-title">饮食记录示例</div>
        <div class="sec-sub">从图片理解到用户确认，再到数据保存。</div>
        <div class="demo-panel">
          <div class="demo-panel-head">
            <span class="demo-panel-title"><span class="widget">HEALTH</span>午餐记录</span>
          </div>
          <div class="demo-panel-body">
            <div class="chat-stream-inner">
              <div v-for="(msg,index) in demoMsgs" :key="index" :class="['chat-row',msg.role]">
                <div :class="['msg-avatar',msg.role]">{{ msg.role === 'assistant' ? 'H' : '我' }}</div>
                <div class="msg-wrap">
                  <div :class="['msg-bubble',msg.role]" style="white-space:pre-wrap">{{ msg.content }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="demo-panel-foot">本助手用于健康信息整理，不替代医生诊断或治疗。</div>
        </div>
      </section>

      <section id="data" class="sec">
        <div class="sec-title">运行概览</div>
        <div class="sec-sub">用户数据与知识库统计仅在管理员登录后加载，公开页面不暴露微信用户标识。</div>
        <div class="console-stats">
          <div class="console-stat">
            <div class="console-stat-icon" style="background:var(--primary-light);color:var(--primary)">HITL</div>
            <div><div class="console-stat-val">确认后执行</div><div class="console-stat-label">敏感健康操作</div></div>
          </div>
          <div class="console-stat">
            <div class="console-stat-icon" style="background:var(--secondary-light);color:var(--secondary)">EVAL</div>
            <div><div class="console-stat-val">8</div><div class="console-stat-label">Agent 行为评测场景</div></div>
          </div>
        </div>
      </section>

      <footer style="text-align:center;padding:52px 0 0;border-top:1px solid var(--border);margin-top:44px">
        <div class="brand-serif" style="font-size:18px;color:var(--text-main)">Health Agent</div>
        <div style="font-size:12px;color:var(--text-muted);margin-top:6px">微信渠道 · 健康管理 · Human-in-the-loop</div>
        <div class="tech-chips">
          <span class="tech-chip">Spring Boot</span>
          <span class="tech-chip">LangChain4j</span>
          <span class="tech-chip">Vue 3</span>
          <span class="tech-chip">MySQL</span>
          <span class="tech-chip">Redis</span>
        </div>
      </footer>
    </div>

    <div v-if="app.showLoginModal" class="preview-overlay" @click="app.showLoginModal=false">
      <div class="login-card" @click.stop>
        <div class="login-logo">H</div>
        <h2>管理员登录</h2>
        <input v-model="app.loginUser" placeholder="用户名" @keyup.enter="app.doLogin" />
        <input v-model="app.loginPass" type="password" @keyup.enter="app.doLogin" placeholder="密码" />
        <button @click="app.doLogin" class="btn btn-primary btn-full">登录</button>
        <p v-if="app.loginError" style="color:var(--danger);font-size:13px;text-align:center;margin-top:12px">{{ app.loginError }}</p>
      </div>
    </div>

  </div>
</template>
