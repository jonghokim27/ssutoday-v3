package kr.ac.ssu.ssutoday.api.reservation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 관리자 작업 페이지 공통 HTML. Discord 버튼으로 여는 페이지라 별도 프런트엔드 없이 서버에서 그린다. */
internal object AdminActionHtml {
    fun page(
        title: String,
        body: String,
    ): String =
        """
        <!DOCTYPE html>
        <html lang="ko">
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${escapeHtml(title)}</title>
            $STYLE
          </head>
          <body>
            <main class="shell">
              <section class="panel">
                $body
              </section>
            </main>
          </body>
        </html>
        """.trimIndent()

    fun resultBody(
        title: String,
        message: String,
        success: Boolean,
        retryUrl: String?,
        details: List<Pair<String, String>> = emptyList(),
    ): String {
        val badge = if (success) "처리 완료" else "다시 확인 필요"
        val toneClass = if (success) "success" else "danger"
        val retryButton = if (success || retryUrl == null) "" else """<a class="secondary" href="${escapeHtml(retryUrl)}">다시 시도</a>"""
        return """
            <div class="badge $toneClass">$badge</div>
            <h1>${escapeHtml(title)}</h1>
            <p class="description">${escapeHtml(message)}</p>
            ${detailList(details)}
            <div class="actions">
              $retryButton
            </div>
            """.trimIndent()
    }

    fun detailList(details: List<Pair<String, String>>): String {
        if (details.isEmpty()) return ""
        return """<dl class="info">${detailRows(details)}</dl>"""
    }

    fun detailRows(details: List<Pair<String, String>>): String =
        details.joinToString("") { (name, value) -> "<div><dt>${escapeHtml(name)}</dt><dd>${escapeHtml(value)}</dd></div>" }

    fun actionUrl(
        token: String,
        action: String,
    ): String = "/admin/action?token=${escapeUrl(token)}&action=${escapeUrl(action)}"

    fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun escapeUrl(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private val STYLE =
        """
        <style>
          :root{
            color-scheme: light;
            --color-text-primary:#0f1222;
            --color-text-secondary:#4f5566;
            --color-text-tertiary:#6b7180;
            --color-text-dim:#9aa0ad;
            --color-surface-base:#ffffff;
            --color-surface-subtle:#f8f9fc;
            --color-surface-panel:#f7f8fb;
            --color-border-default:#edeef4;
            --color-border-soft:#f1f2f7;
            --color-brand-blue:#4f7cff;
            --color-brand-gradient:linear-gradient(135deg,#4f7cff,#9b5cff);
            --color-success:#1fb97a;
            --color-success-bg:#e8faf1;
            --color-danger:#ff4d63;
            --color-danger-soft:#ffecef;
            --color-orange:#ff6b3d;
            --shadow-card:0 10px 26px -20px rgba(40,30,90,.35);
            --shadow-primary-button:0 16px 32px -12px rgba(106,76,255,.6);
            --font-family-base:Pretendard,-apple-system,BlinkMacSystemFont,system-ui,sans-serif;
          }
          *{box-sizing:border-box}
          body{
            margin:0;
            min-height:100vh;
            font-family:var(--font-family-base);
            color:var(--color-text-primary);
            word-break:keep-all;
            background:
              radial-gradient(circle at top,#eef3ff 0,#f8f9fc 38%,#f8f9fc 100%);
          }
          .shell{
            min-height:100vh;
            display:flex;
            align-items:center;
            justify-content:center;
            padding:24px;
          }
          .panel{
            width:min(100%,420px);
            padding:28px 24px 24px;
            border-radius:22px;
            background:rgba(255,255,255,.92);
            border:1px solid var(--color-border-soft);
            box-shadow:var(--shadow-card);
            backdrop-filter:blur(16px);
            animation:floatUp .22s ease-out;
          }
          .badge{
            display:inline-flex;
            align-items:center;
            padding:7px 12px;
            border-radius:999px;
            background:rgba(79,124,255,.1);
            color:#315fdb;
            font-size:12px;
            font-weight:700;
          }
          .badge.success{
            background:var(--color-success-bg);
            color:var(--color-success);
          }
          .badge.danger{
            background:var(--color-danger-soft);
            color:var(--color-danger);
          }
          h1{
            margin:16px 0 10px;
            font-size:24px;
            line-height:1.25;
          }
          .description{
            margin:0 0 22px;
            color:var(--color-text-secondary);
            font-size:14px;
            line-height:1.6;
          }
          .form{
            display:grid;
            gap:16px;
          }
          .field{
            display:grid;
            gap:8px;
          }
          .field > span{
            font-size:13px;
            font-weight:700;
            color:var(--color-text-primary);
          }
          input[type=text],input:not([type]),textarea{
            width:100%;
            padding:14px 15px;
            border:1px solid var(--color-border-default);
            border-radius:18px;
            background:var(--color-surface-panel);
            color:var(--color-text-primary);
            resize:vertical;
            font-family:inherit;
            outline:none;
            font-size:14px;
            line-height:1.5;
          }
          input:not([type]),input[type=text]{
            min-height:52px;
          }
          textarea{
            min-height:116px;
          }
          input:focus,textarea:focus{
            border-color:#7b89ff;
            box-shadow:0 0 0 4px rgba(79,124,255,.12);
          }
          .primary,.secondary{
            display:inline-flex;
            justify-content:center;
            align-items:center;
            min-height:52px;
            width:100%;
            border:none;
            border-radius:18px;
            text-decoration:none;
            font-size:15px;
            font-weight:700;
          }
          .primary{
            color:#fff;
            background:var(--color-brand-gradient);
            box-shadow:var(--shadow-primary-button);
          }
          .primary.danger{
            background:linear-gradient(135deg,#ff6b6b,#ff4d63);
            box-shadow:0 16px 32px -12px rgba(255,77,99,.55);
          }
          .secondary{
            color:var(--color-text-primary);
            background:var(--color-surface-panel);
            border:1px solid var(--color-border-default);
          }
          .actions{
            display:grid;
            gap:12px;
            margin-top:22px;
          }
          .info{
            margin:0 0 16px;
            padding:12px 14px;
            border-radius:16px;
            background:var(--color-surface-panel);
          }
          .info div{
            display:flex;
            gap:10px;
            padding:4px 0;
            font-size:13px;
            line-height:1.45;
          }
          .info dt{
            width:72px;
            flex:none;
            color:var(--color-text-dim);
            font-weight:600;
          }
          .info dd{
            margin:0;
            color:#2c3140;
            font-weight:700;
          }
          .thumb{
            display:block;
            width:100%;
            max-height:260px;
            margin:8px 0 4px;
            border-radius:12px;
            object-fit:cover;
          }
          .change{
            display:flex;
            align-items:center;
            justify-content:space-between;
            gap:8px;
            margin:0 0 18px;
            padding:12px 14px;
            border-radius:16px;
            border:1px solid var(--color-border-default);
            font-size:13px;
          }
          .change span{
            flex:none;
            color:var(--color-text-tertiary);
            font-weight:700;
          }
          .change strong{
            text-align:right;
            font-weight:800;
          }
          .change s{
            color:var(--color-text-dim);
            text-decoration:none;
            font-weight:600;
          }
          .change em{
            color:var(--color-brand-blue);
            font-style:normal;
          }
          .options{
            display:grid;
            gap:8px;
          }
          .opt{
            display:flex;
            gap:11px;
            padding:13px 14px;
            border-radius:16px;
            border:1.5px solid var(--color-border-default);
            background:#fff;
            cursor:pointer;
          }
          .opt input{
            width:18px;
            height:18px;
            margin:2px 0 0;
            flex:none;
            accent-color:var(--color-danger);
          }
          .opt strong{
            display:block;
            font-size:14px;
            font-weight:800;
          }
          .opt small{
            display:block;
            margin-top:2px;
            color:var(--color-text-tertiary);
            font-size:12px;
            font-weight:600;
            line-height:1.45;
          }
          .opt small.after{
            color:var(--color-orange);
            font-weight:700;
          }
          .opt small.blocked{
            color:var(--color-danger);
            font-weight:700;
          }
          .opt:has(input:checked){
            border-color:var(--color-danger);
            background:#fff8f9;
          }
          .opt:has(input:disabled){
            cursor:default;
            opacity:.6;
          }
          .chips{
            display:flex;
            flex-wrap:wrap;
            gap:6px;
          }
          .chip{
            display:inline-flex;
            align-items:center;
            padding:9px 12px;
            border-radius:12px;
            border:1px solid var(--color-border-default);
            background:#fff;
            color:var(--color-text-secondary);
            font-size:13px;
            font-weight:700;
            cursor:pointer;
          }
          .chip input{
            position:absolute;
            opacity:0;
            pointer-events:none;
          }
          .chip:has(input:checked){
            border-color:transparent;
            background:var(--color-text-primary);
            color:#fff;
          }
          .check{
            display:flex;
            gap:10px;
            align-items:flex-start;
            padding:12px 14px;
            border-radius:16px;
            background:var(--color-surface-panel);
            font-size:13px;
            font-weight:700;
            line-height:1.45;
            cursor:pointer;
          }
          .check input{
            width:18px;
            height:18px;
            margin:1px 0 0;
            flex:none;
            accent-color:var(--color-danger);
          }
          .check small{
            display:block;
            margin-top:2px;
            color:var(--color-text-tertiary);
            font-size:12px;
            font-weight:600;
          }
          .note{
            margin:-4px 2px 0;
            color:var(--color-text-tertiary);
            font-size:12px;
            font-weight:600;
            line-height:1.5;
          }
          [hidden]{
            display:none !important;
          }
          @keyframes floatUp{
            from{opacity:0;transform:translateY(14px)}
            to{opacity:1;transform:translateY(0)}
          }
        </style>
        """.trimIndent()
}
