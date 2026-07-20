import { ArrowLeft, FileText, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge } from "../../components/common/Badge";
import { Card } from "../../components/common/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { getSharedTicketConversation } from "../../services/legalTicket.service";
import type { LegalTicket } from "../../types/legalTicket";

export function SharedTicketConversationPage() {
  const { token = "" } = useParams();
  const [ticket, setTicket] = useState<LegalTicket | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void getSharedTicketConversation(token)
      .then((value) => { if (active) setTicket(value); })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : "Không thể mở link chia sẻ.");
      });
    return () => { active = false; };
  }, [token]);

  return <div className="space-y-gutter">
    <PageHeader title={ticket?.title || "Cuộc hội thoại được chia sẻ"}
      subtitle="Chỉ participant của ticket đã đăng nhập mới có thể xem nội dung này."
      actions={<Link className="inline-flex items-center gap-xs text-sm font-semibold text-primary" to={ticket ? `/tickets/${ticket.id}` : "/tickets"}><ArrowLeft className="h-4 w-4" />Mở ticket</Link>} />
    {error && <div className="rounded-xl border border-error/40 bg-error/10 p-md text-sm text-error" role="alert">{error}</div>}
    {!ticket && !error && <Card>Đang xác thực link chia sẻ…</Card>}
    {ticket && <div className="grid gap-gutter xl:grid-cols-[minmax(0,1fr)_360px]">
      <main className="space-y-gutter">
        <Card title={ticket.title || ticket.question || ticket.id}
          subtitle={ticket.description || "Context bất biến tại thời điểm tạo ticket"}
          actions={ticket.status ? <Badge>{ticket.status}</Badge> : undefined}>
          <div className="flex items-center gap-xs text-sm text-success"><ShieldCheck className="h-4 w-4" />Link đã được xác thực, có thời hạn và chỉ dành cho participant.</div>
        </Card>
        <Card title="Câu hỏi của người dùng"><p className="whitespace-pre-line text-sm leading-6">{ticket.contextSnapshot?.userQuestion || ticket.question || "—"}</p></Card>
        <Card title="Câu trả lời AI"><p className="whitespace-pre-line text-sm leading-6 text-on-surface-variant">{ticket.contextSnapshot?.assistantAnswer || ticket.answer || "—"}</p></Card>
      </main>
      <aside className="space-y-gutter">
        <Card title="Nguồn và tài liệu">
          <div className="space-y-sm text-sm">
            <p className="flex items-center gap-xs font-semibold"><FileText className="h-4 w-4" />{ticket.sharedDocumentIds?.length || 0} tài liệu được chia sẻ</p>
            <pre className="max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-low p-sm text-xs">{ticket.contextSnapshot?.documentSnapshotJson || "[]"}</pre>
          </div>
        </Card>
        <Card title="Citation snapshot"><pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-container-low p-sm text-xs">{ticket.contextSnapshot?.citationSnapshotJson || "[]"}</pre></Card>
      </aside>
    </div>}
  </div>;
}
