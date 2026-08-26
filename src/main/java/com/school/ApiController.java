package com.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

record ClassRequest(@NotBlank String name,@NotBlank String section,Boolean active) {}
record StudentRequest(@NotNull Long classId,@NotBlank String admissionNumber,@NotBlank String name,String fatherName,String motherName,String guardianName,String phone,Boolean active) {}
record FeeTypeRequest(@NotBlank String code,@NotBlank String displayName,Boolean active) {}
record Breakdown(String feeTypeCode,String feeTypeName,BigDecimal amount) {}
record ReportResponse(String period,List<Breakdown> breakdown,BigDecimal total) {}

@RestController @RequestMapping("/api") class ApiController {
  private final FeeService fees; private final ClassRepository classes; private final StudentRepository students; private final FeeTypeRepository types; private final ReceiptRepository receipts; private final SchoolRepository schools;
  ApiController(FeeService f,ClassRepository c,StudentRepository s,FeeTypeRepository t,ReceiptRepository r,SchoolRepository sc){fees=f;classes=c;students=s;types=t;receipts=r;schools=sc;}
  private Long school(Authentication a,Long requested){return fees.scope(fees.actor(a),requested);}
  @GetMapping("/classes") List<SchoolClass> listClasses(Authentication a,@RequestParam(required=false) Long schoolId){return classes.findBySchoolId(school(a,schoolId));}
  @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED) SchoolClass createClass(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody ClassRequest q){School sc=schools.getReferenceById(school(a,schoolId));SchoolClass c=new SchoolClass();c.school=sc;c.name=q.name();c.section=q.section();c.active=q.active()==null||q.active();return classes.save(c);}
  @GetMapping("/students") List<Student> listStudents(Authentication a,@RequestParam(required=false) Long schoolId){return students.findBySchoolId(school(a,schoolId));}
  @PostMapping("/students") @ResponseStatus(HttpStatus.CREATED) Student createStudent(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody StudentRequest q){Long sid=school(a,schoolId);SchoolClass c=classes.findByIdAndSchoolId(q.classId(),sid).orElseThrow(()->new NoSuchElementException("Class not found"));Student s=new Student();s.school=c.school;s.schoolClass=c;s.admissionNumber=q.admissionNumber();s.name=q.name();s.fatherName=q.fatherName();s.motherName=q.motherName();s.guardianName=q.guardianName();s.phone=q.phone();s.active=q.active()==null||q.active();return students.save(s);}
  @GetMapping("/fee-types") List<FeeType> listTypes(Authentication a,@RequestParam(required=false) Long schoolId){return types.findBySchoolIdAndActiveTrue(school(a,schoolId));}
  @PostMapping("/fee-types") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED) FeeType createType(Authentication a,@RequestParam Long schoolId,@Valid @RequestBody FeeTypeRequest q){FeeType f=new FeeType();f.school=schools.getReferenceById(school(a,schoolId));f.code=q.code().toUpperCase(Locale.ROOT);f.displayName=q.displayName();f.active=q.active()==null||q.active();return types.save(f);}
  @GetMapping("/fees") List<ReceiptResponse> listReceipts(Authentication a,@RequestParam(required=false) Long schoolId){return receipts.findBySchoolIdOrderByCreatedAtDesc(school(a,schoolId)).stream().map(fees::view).toList();}
  @GetMapping("/fees/{id}") ReceiptResponse receipt(Authentication a,@RequestParam(required=false) Long schoolId,@PathVariable Long id){return fees.view(receipts.findByIdAndSchoolId(id,school(a,schoolId)).orElseThrow(()->new NoSuchElementException("Receipt not found")));}
  @PostMapping("/fees") @ResponseStatus(HttpStatus.CREATED) ReceiptResponse createFee(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody CreateFeeRequest q){return fees.create(a,schoolId,q);}
  @PostMapping("/fees/{id}/cancel") ReceiptResponse cancel(Authentication a,@RequestParam Long schoolId,@PathVariable Long id,@Valid @RequestBody CancelRequest q){return fees.cancel(a,id,q,schoolId);}
  @GetMapping("/reports/fees/monthly") ReportResponse monthly(Authentication a,@RequestParam(required=false) Long schoolId,@RequestParam @Min(2000) int year,@RequestParam @Min(1) @Max(12) int month,@RequestParam(required=false) Long classId){Long sid=school(a,schoolId);LocalDate start=LocalDate.of(year,month,1),end=start.plusMonths(1);return report(sid,start,end,classId,String.format("%d-%02d",year,month));}
  @GetMapping("/reports/fees/yearly") ReportResponse yearly(Authentication a,@RequestParam(required=false) Long schoolId,@RequestParam @Min(2000) int startYear,@RequestParam(required=false) Long classId){Long sid=school(a,schoolId);LocalDate start=LocalDate.of(startYear,4,1);return report(sid,start,start.plusYears(1),classId,startYear+"-"+(startYear+1));}
  private ReportResponse report(Long sid,LocalDate start,LocalDate end,Long classId,String period){List<Breakdown>b=receipts.totals(sid,start,end,classId).stream().map(x->new Breakdown((String)x[0],(String)x[1],(BigDecimal)x[2])).toList();return new ReportResponse(period,b,b.stream().map(Breakdown::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}
  @ExceptionHandler({NoSuchElementException.class,IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<Map<String,String>> bad(RuntimeException e){return ResponseEntity.badRequest().body(Map.of("code",e.getClass().getSimpleName(),"message",e.getMessage()));}
}

