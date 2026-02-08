import Link from "next/link";

export default function UploadPage() {
  return (
    <main className="page-stack">
      <section className="panel">
        <p className="hero-kicker">上传入口</p>
        <h2 style={{ fontFamily: "var(--font-heading)", marginTop: 0 }}>上传功能已迁移到右上角</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          请点击页面右上角“上传”按钮，在弹窗中选择文件、指定疾病分类并可直接新增疾病。
        </p>
        <div className="actions">
          <Link className="btn btn-ghost" href="/">
            返回主页面时间线
          </Link>
        </div>
      </section>
    </main>
  );
}
