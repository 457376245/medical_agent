type DataRightsActionsProps = {
  onRequestExport: () => void;
  onRequestDelete: () => void;
  downloadUrl?: string;
};

export function DataRightsActions({ onRequestExport, onRequestDelete, downloadUrl }: DataRightsActionsProps) {
  return (
    <section className="panel">
      <h3 className="panel-title-small">数据权益操作</h3>
      <p className="muted panel-subtitle">
        导出将生成可下载的数据包，删除申请会进入合规审批流程。
      </p>
      <div className="actions">
        <button className="btn btn-primary" type="button" onClick={onRequestExport}>
          申请导出
        </button>
        <button className="btn btn-danger" type="button" onClick={onRequestDelete}>
          申请删除
        </button>
      </div>
      {downloadUrl && (
        <p className="mt-10">
          <a className="btn btn-ghost" href={downloadUrl} target="_blank" rel="noreferrer">
            下载导出文件
          </a>
        </p>
      )}
    </section>
  );
}
