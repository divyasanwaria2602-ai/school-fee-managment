package com.school;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

record FeeItemRequest(@NotNull Long feeTypeId) {}
record CreateFeeRequest(@NotNull Long studentId, @NotNull LocalDate paymentDate,
    @NotEmpty List<@Valid FeeItemRequest> items, String notes) {}
record CancelRequest(@NotBlank @Size(max=500) String reason) {}
record ItemResponse(Long feeTypeId, String feeTypeCode, String feeTypeName, BigDecimal amount) {}
record ReceiptResponse(Long id, String receiptNumber, Long studentId, String studentName,
    String admissionNumber, String className, String section, LocalDate paymentDate,
    ReceiptStatus status, BigDecimal totalAmount, String notes, List<ItemResponse> items) {}

@Service
class FeeService {
  private final UserRepository users; private final StudentRepository students; private final FeeTypeRepository types;
  private final ReceiptRepository receipts; private final AuditRepository audits; private final EntityManager em;
  private final ClassFeeStructureRepository structures;
  private final AuthorizationService authorization;

  FeeService(UserRepository u, StudentRepository s, FeeTypeRepository t, ReceiptRepository r,
      AuditRepository a, EntityManager e, ClassFeeStructureRepository structures, AuthorizationService authorization) {
    users=u; students=s; types=t; receipts=r; audits=a; em=e; this.structures=structures; this.authorization=authorization;
  }

  AppUser actor(Authentication auth) {
    return authorization.actor(auth);
  }

  Long scope(AppUser user, Long requested) {
    return authorization.schoolId(user, requested);
  }

  @Transactional
  ReceiptResponse create(Authentication auth, Long requestedSchoolId, CreateFeeRequest request) {
    AppUser user=actor(auth);
    authorization.requireSchoolStaff(user);
    Long schoolId=scope(user,requestedSchoolId);
    Student student=students.findByIdAndSchoolId(request.studentId(),schoolId).filter(s->s.active)
        .orElseThrow(() -> new NoSuchElementException("Active student not found"));
    if (request.items().stream().map(FeeItemRequest::feeTypeId).distinct().count()!=request.items().size())
      throw new IllegalArgumentException("A fee type may appear only once");
    String academicYear=academicYear(request.paymentDate());
    List<ClassFeeStructure> configured=new ArrayList<>();
    for(FeeItemRequest i:request.items()) {
      configured.add(structures.findBySchoolIdAndSchoolClass_IdAndFeeType_IdAndAcademicYear(schoolId,student.schoolClass.id,i.feeTypeId(),academicYear)
          .filter(x->x.active).orElseThrow(() -> new IllegalArgumentException("Fee is not configured for this class and academic year")));
    }
    BigDecimal total=configured.stream().map(x->x.amount).reduce(BigDecimal.ZERO,BigDecimal::add);
    if(total.signum()<=0) throw new IllegalArgumentException("Selected fee total must be positive");
    Integer year=request.paymentDate().getYear();
    Number serial=(Number)em.createNativeQuery("INSERT INTO receipt_number_sequences(school_id,receipt_year,last_value) VALUES (?1,?2,1) ON CONFLICT(school_id,receipt_year) DO UPDATE SET last_value=receipt_number_sequences.last_value+1 RETURNING last_value")
        .setParameter(1,schoolId).setParameter(2,year).getSingleResult();
    FeeReceipt receipt=new FeeReceipt(); receipt.school=student.school; receipt.student=student; receipt.createdBy=user;
    receipt.paymentDate=request.paymentDate(); receipt.totalAmount=total; receipt.receiptNumber="%d-%06d".formatted(year,serial.longValue()); receipt.notes=request.notes();
    for(ClassFeeStructure configuredFee:configured) { FeeReceiptItem item=new FeeReceiptItem(); item.receipt=receipt; item.feeType=configuredFee.feeType; item.amount=configuredFee.amount; receipt.items.add(item); }
    receipts.save(receipt);
    audit(user,receipt.school,"CREATE_FEE","FEE_RECEIPT",receipt.id,null,"{\"receiptNumber\":\""+receipt.receiptNumber+"\"}");
    return view(receipt);
  }

  @Transactional
  ReceiptResponse cancel(Authentication auth, Long id, CancelRequest request, Long schoolId) {
    AppUser user=actor(auth);
    authorization.requireSchoolAdmin(user);
    FeeReceipt receipt=receipts.findByIdAndSchoolId(id,scope(user,schoolId)).orElseThrow(() -> new NoSuchElementException("Receipt not found"));
    if(receipt.status!=ReceiptStatus.ACTIVE) throw new IllegalStateException("Receipt is already cancelled");
    receipt.status=ReceiptStatus.CANCELLED; receipt.cancellationReason=request.reason(); receipt.cancelledAt=Instant.now(); receipt.cancelledBy=user;
    audit(user,receipt.school,"CANCEL_FEE","FEE_RECEIPT",id,"{\"status\":\"ACTIVE\"}","{\"status\":\"CANCELLED\",\"reason\":\""+request.reason().replace("\"","\\\"")+"\"}");
    return view(receipt);
  }

  ReceiptResponse view(FeeReceipt r) { String cn=r.student.schoolClass!=null?r.student.schoolClass.name:""; String sec=r.student.schoolClass!=null?r.student.schoolClass.section:"";
    return new ReceiptResponse(r.id,r.receiptNumber,r.student.id,r.student.name,r.student.admissionNumber,cn,sec,r.paymentDate,r.status,r.totalAmount,r.notes,
      r.items.stream().map(i->new ItemResponse(i.feeType.id,i.feeType.code,i.feeType.displayName,i.amount)).toList()); }
  void audit(AppUser u, School s, String action, String type, Long id, String oldV, String newV) { AuditLog a=new AuditLog(); a.user=u;a.school=s;a.action=action;a.entityType=type;a.entityId=id;a.oldValue=oldV;a.newValue=newV;audits.save(a); }
  static String academicYear(LocalDate d) { int start=d.getMonthValue()>=4?d.getYear():d.getYear()-1; return "%d-%02d".formatted(start,(start+1)%100); }
}
