package com.school;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

record FeeItemRequest(@NotNull Long feeTypeId, @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
record CreateFeeRequest(@NotNull Long studentId, @NotNull LocalDate paymentDate, @NotEmpty List<@Valid FeeItemRequest> items) {}
record CancelRequest(@NotBlank @Size(max=500) String reason) {}
record ItemResponse(Long feeTypeId,String feeTypeCode,String feeTypeName,BigDecimal amount) {}
record ReceiptResponse(Long id,String receiptNumber,Long studentId,String studentName,String admissionNumber,String className,String section,LocalDate paymentDate,ReceiptStatus status,BigDecimal totalAmount,List<ItemResponse> items) {}

@Service class FeeService {
  private final UserRepository users; private final StudentRepository students; private final FeeTypeRepository types; private final ReceiptRepository receipts; private final AuditRepository audits; private final EntityManager em;
  FeeService(UserRepository u,StudentRepository s,FeeTypeRepository t,ReceiptRepository r,AuditRepository a,EntityManager e){users=u;students=s;types=t;receipts=r;audits=a;em=e;}
  AppUser actor(Authentication auth) { return users.findByUsernameAndActiveTrue(auth.getName()).orElseThrow(()->new AccessDeniedException("Active user required")); }
  Long scope(AppUser u, Long requested) { if(u.role==Role.ADMIN) { if(requested==null) throw new IllegalArgumentException("schoolId is required for an administrator"); return requested; } return u.school.id; }
  @Transactional ReceiptResponse create(Authentication auth, Long requestedSchoolId, CreateFeeRequest request) {
    AppUser user=actor(auth); Long schoolId=scope(user,requestedSchoolId); Student student=students.findByIdAndSchoolId(request.studentId(),schoolId).filter(s->s.active).orElseThrow(()->new NoSuchElementException("Active student not found"));
    if(request.items().stream().map(FeeItemRequest::feeTypeId).distinct().count()!=request.items().size()) throw new IllegalArgumentException("A fee type may appear only once");
    BigDecimal total=request.items().stream().map(FeeItemRequest::amount).reduce(BigDecimal.ZERO,BigDecimal::add); if(total.signum()<=0) throw new IllegalArgumentException("Total must be positive");
    List<FeeType> feeTypes=new ArrayList<>(); for(FeeItemRequest i:request.items()) feeTypes.add(types.findByIdAndSchoolIdAndActiveTrue(i.feeTypeId(),schoolId).orElseThrow(()->new IllegalArgumentException("Invalid fee type")));
    Integer year=request.paymentDate().getYear(); Number serial=(Number)em.createNativeQuery("INSERT INTO receipt_number_sequences(school_id,receipt_year,last_value) VALUES (?1,?2,1) ON CONFLICT(school_id,receipt_year) DO UPDATE SET last_value=receipt_number_sequences.last_value+1 RETURNING last_value").setParameter(1,schoolId).setParameter(2,year).getSingleResult();
    FeeReceipt receipt=new FeeReceipt(); receipt.school=student.school; receipt.student=student; receipt.createdBy=user; receipt.paymentDate=request.paymentDate(); receipt.totalAmount=total; receipt.receiptNumber="%d-%06d".formatted(year,serial.longValue());
    for(int i=0;i<request.items().size();i++){ FeeReceiptItem item=new FeeReceiptItem(); item.receipt=receipt; item.feeType=feeTypes.get(i); item.amount=request.items().get(i).amount(); receipt.items.add(item); }
    receipts.save(receipt); audit(user,receipt.school,"CREATE_FEE","FEE_RECEIPT",receipt.id,null,"{\"receiptNumber\":\""+receipt.receiptNumber+"\"}"); return view(receipt);
  }
  @Transactional ReceiptResponse cancel(Authentication auth,Long id,CancelRequest request,Long schoolId) { AppUser user=actor(auth); if(user.role!=Role.ADMIN) throw new AccessDeniedException("Only administrators may cancel receipts"); FeeReceipt receipt=receipts.findByIdAndSchoolId(id,scope(user,schoolId)).orElseThrow(()->new NoSuchElementException("Receipt not found")); if(receipt.status!=ReceiptStatus.ACTIVE) throw new IllegalStateException("Receipt is already cancelled"); receipt.status=ReceiptStatus.CANCELLED;receipt.cancellationReason=request.reason();receipt.cancelledAt=Instant.now();receipt.cancelledBy=user;audit(user,receipt.school,"CANCEL_FEE","FEE_RECEIPT",id,"{\"status\":\"ACTIVE\"}","{\"status\":\"CANCELLED\"}"); return view(receipt); }
  ReceiptResponse view(FeeReceipt r) { return new ReceiptResponse(r.id,r.receiptNumber,r.student.id,r.student.name,r.student.admissionNumber,r.student.schoolClass.name,r.student.schoolClass.section,r.paymentDate,r.status,r.totalAmount,r.items.stream().map(i->new ItemResponse(i.feeType.id,i.feeType.code,i.feeType.displayName,i.amount)).toList()); }
  void audit(AppUser u,School s,String action,String type,Long id,String oldV,String newV){AuditLog a=new AuditLog();a.user=u;a.school=s;a.action=action;a.entityType=type;a.entityId=id;a.oldValue=oldV;a.newValue=newV;audits.save(a);}
}
