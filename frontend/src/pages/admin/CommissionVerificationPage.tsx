import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "../../components/common/Button";
import { Card } from "../../components/common/Card";
import { verifyCommissionChange } from "../../services/revenuePayroll.service";
import { useI18n } from "../../hooks/useI18n";

export function CommissionVerificationPage(){const {t}=useI18n();const [params]=useSearchParams();const requestId=params.get("requestId")||"";const token=params.get("token")||"";const [state,setState]=useState<"idle"|"busy"|"done"|"error">("idle");const [message,setMessage]=useState("");return <div className="mx-auto max-w-xl py-2xl"><Card title={t('admin.commissionVerification.title')} subtitle={t('admin.commissionVerification.subtitle')}><div className="space-y-md"><p className="text-sm">{t('admin.commissionVerification.requestCode')}: <b>{requestId||t('admin.commissionVerification.invalid')}</b></p>{state==="done"?<div className="rounded-xl bg-emerald-500/10 p-md text-emerald-600">{t('admin.commissionVerification.success')}</div>:<Button disabled={!requestId||!token||state==="busy"} onClick={async()=>{setState("busy");try{await verifyCommissionChange(requestId,token);setState("done");}catch(e){setState("error");setMessage(e instanceof Error?e.message:t('admin.commissionVerification.error'));}}}>{t('admin.commissionVerification.confirm')}</Button>}{state==="error"&&<p className="text-error">{message}</p>}<Link className="block text-sm text-primary" to="/admin/revenue">{t('admin.commissionVerification.back')}</Link></div></Card></div>;}
