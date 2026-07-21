package com.analyzer.api.service.impl;

import com.analyzer.api.dto.revenue.RevenuePayrollDtos;
import com.analyzer.api.exception.common.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class RevenueWorkbookService {
    private final RevenuePayrollService payroll;
    @Transactional(readOnly=true) public byte[] adminPeriod(String periodId,Long expertId){
        List<RevenuePayrollDtos.Statement> statements=payroll.periodStatements(periodId).stream().filter(s->expertId==null||s.expertId().equals(expertId)).map(s->payroll.adminStatement(s.id())).toList();
        if(statements.isEmpty())throw new ResourceNotFoundException("REVENUE_EXPORT_EMPTY"); return workbook(statements);
    }
    @Transactional(readOnly=true) public byte[] expertStatement(Long expertId,String statementId){return workbook(List.of(payroll.expertStatement(expertId,statementId)));}
    private byte[] workbook(List<RevenuePayrollDtos.Statement> statements){
        try(XSSFWorkbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            summary(wb,statements);tickets(wb,statements);adjustments(wb,statements);payouts(wb,statements);
            for(Sheet s:wb)for(int i=0;i<s.getRow(0).getLastCellNum();i++)s.autoSizeColumn(i);
            wb.write(out);return out.toByteArray();
        }catch(IOException e){throw new ConflictException("REVENUE_EXPORT_FAILED");}
    }
    private void summary(Workbook wb,List<RevenuePayrollDtos.Statement> list){Sheet s=sheet(wb,"Summary",List.of("Period","Status","Expert","Gross fee","Platform fee","Expert payout","Adjustments","Final payout","Paid","Remaining","Payment reference","Generated at","Confirmed at","Paid at"));for(var x:list)row(s,List.of(x.period().periodCode(),x.status().name(),x.expertNameSnapshot(),x.grossConsultationFee(),x.totalPlatformFee(),x.totalExpertPayout(),x.adjustmentAmount(),x.finalPayout(),x.paidAmount(),x.remainingAmount(),n(x.paymentReference()),x.generatedAt(),n(x.confirmedAt()),n(x.paidAt())));}
    private void tickets(Workbook wb,List<RevenuePayrollDtos.Statement> list){Sheet s=sheet(wb,"Ticket Details",List.of("Period","Expert","Ticket code","Ticket ID","Consultation fee","Commission rate","Platform fee","Expert payout","Recognized at","Status snapshot"));for(var x:list)for(var i:x.items())row(s,List.of(x.period().periodCode(),x.expertNameSnapshot(),i.ticketCode(),i.ticketId(),i.consultationFee(),i.commissionRateSnapshot(),i.platformFee(),i.expertPayout(),i.recognizedAt(),i.ticketStatusSnapshot().name()));}
    private void adjustments(Workbook wb,List<RevenuePayrollDtos.Statement> list){Sheet s=sheet(wb,"Adjustments",List.of("Period","Expert","Type","Amount","Ticket ID","Reason","Created at"));for(var x:list)for(var a:x.adjustments())row(s,List.of(x.period().periodCode(),x.expertNameSnapshot(),a.type().name(),a.amount(),n(a.ticketId()),a.reason(),a.createdAt()));}
    private void payouts(Workbook wb,List<RevenuePayrollDtos.Statement> list){Sheet s=sheet(wb,"Payout Transactions",List.of("Period","Expert","Type","Status","Amount","Payment reference","Paid at"));for(var x:list)for(var p:x.payouts())row(s,List.of(x.period().periodCode(),x.expertNameSnapshot(),p.type().name(),p.status().name(),p.amount(),n(p.paymentReference()),n(p.paidAt())));}
    private Sheet sheet(Workbook wb,String name,List<String> headers){Sheet s=wb.createSheet(name);Row r=s.createRow(0);for(int i=0;i<headers.size();i++)r.createCell(i).setCellValue(headers.get(i));s.createFreezePane(0,1);return s;}
    private void row(Sheet s,List<?> values){Row r=s.createRow(s.getLastRowNum()+1);for(int i=0;i<values.size();i++){Object v=values.get(i);Cell c=r.createCell(i);if(v instanceof BigDecimal b)c.setCellValue(b.doubleValue());else c.setCellValue(safe(String.valueOf(v)));}}
    private Object n(Object value){return value==null?"":value;} private String safe(String value){return value.matches("^[=+\\-@].*")?"'"+value:value;}
}
