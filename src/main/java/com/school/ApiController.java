package com.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

record ClassRequest(@NotBlank String name, String section, Boolean active) {}
record StudentRequest(@NotNull Long classId,@NotBlank String admissionNumber,@NotBlank String name,String fatherName,String motherName,String guardianName,String phone,Boolean active) {}
record ClassRef(Long id,String name,String section) {}
record StudentResponse(Long id,String admissionNumber,String name,String fatherName,String motherName,String guardianName,String phone,Boolean active,ClassRef schoolClass) {}
record FeeTypeRequest(@NotBlank String code,@NotBlank String displayName,Boolean active) {}
record Breakdown(String feeTypeCode,String feeTypeName,BigDecimal amount) {}
record ReportResponse(String period,List<Breakdown> breakdown,BigDecimal total) {}
record UpdateStudentRequest(Boolean active) {}

@RestController @RequestMapping("/api")
class ApiController {
  private final FeeService fees; private final ClassRepository classes; private final StudentRepository students;
  private final FeeTypeRepository types; private final ReceiptRepository receipts; private final SchoolRepository schools;
  private final UserManagementService userManagement; private final FeeStructureService feeStructures;
  ApiController(FeeService f,ClassRepository c,StudentRepository s,FeeTypeRepository t,ReceiptRepository r,SchoolRepository sc,UserManagementService um,FeeStructureService fs){fees=f;classes=c;students=s;types=t;receipts=r;schools=sc;userManagement=um;feeStructures=fs;}

  private Long school(Authentication a,Long requested){return fees.scope(fees.actor(a),requested);}
  @GetMapping("/me") UserResponse me(Authentication a){ return userManagement.view(fees.actor(a)); }

  @GetMapping("/schools/{id}") School getSchool(Authentication a,@PathVariable Long id){
    AppUser u=fees.actor(a); if(u.role!=Role.ROOT && (u.school==null || !u.school.id.equals(id))) throw new AccessDeniedException("School access denied");
    return schools.findById(id).orElseThrow(()->new NoSuchElementException("School not found"));
  }
  @GetMapping("/schools") @PreAuthorize("hasRole('ROOT')") List<School> schools(){return schools.findByOrderByName();}
  @PostMapping("/schools") @PreAuthorize("hasRole('ROOT')") @ResponseStatus(HttpStatus.CREATED) School createSchool(Authentication a,@Valid @RequestBody CreateSchoolRequest q){return userManagement.createSchool(a,q);}

  @GetMapping("/classes") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") List<SchoolClass> listClasses(Authentication a,@RequestParam(required=false) Long schoolId){return classes.findBySchoolIdOrderByNameAscSectionAsc(school(a,schoolId));}
  @PostMapping("/classes") @PreAuthorize("hasRole('SCHOOL_ADMIN')") @ResponseStatus(HttpStatus.CREATED) SchoolClass createClass(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody ClassRequest q){School sc=schools.getReferenceById(school(a,schoolId));SchoolClass c=new SchoolClass();c.school=sc;c.name=q.name();c.section=q.section()==null?"":q.section();c.active=q.active()==null||q.active();return classes.save(c);}

  @GetMapping("/students") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") List<StudentResponse> listStudents(Authentication a,@RequestParam(required=false) Long schoolId){return students.findBySchoolId(school(a,schoolId)).stream().map(s->new StudentResponse(s.id,s.admissionNumber,s.name,s.fatherName,s.motherName,s.guardianName,s.phone,s.active,new ClassRef(s.schoolClass.id,s.schoolClass.name,s.schoolClass.section))).toList();}
  @PostMapping("/students") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") @ResponseStatus(HttpStatus.CREATED) StudentResponse createStudent(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody StudentRequest q){Long sid=school(a,schoolId);SchoolClass c=classes.findByIdAndSchoolId(q.classId(),sid).orElseThrow(()->new NoSuchElementException("Class not found"));if(students.findBySchoolIdAndAdmissionNumber(sid,q.admissionNumber()).isPresent())throw new IllegalArgumentException("Admission number already exists for this school");Student s=new Student();s.school=c.school;s.schoolClass=c;s.admissionNumber=q.admissionNumber();s.name=q.name();s.fatherName=q.fatherName();s.motherName=q.motherName();s.guardianName=q.guardianName();s.phone=q.phone();s.active=q.active()==null||q.active();Student saved=students.save(s);return new StudentResponse(saved.id,saved.admissionNumber,saved.name,saved.fatherName,saved.motherName,saved.guardianName,saved.phone,saved.active,new ClassRef(c.id,c.name,c.section));}
  @PatchMapping("/students/{id}") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") StudentResponse updateStudent(Authentication a,@RequestParam(required=false) Long schoolId,@PathVariable Long id,@RequestBody UpdateStudentRequest q){Long sid=school(a,schoolId);Student s=students.findByIdAndSchoolId(id,sid).orElseThrow(()->new NoSuchElementException("Student not found"));if(q.active()!=null)s.active=q.active();Student saved=students.save(s);SchoolClass sc=saved.schoolClass;return new StudentResponse(saved.id,saved.admissionNumber,saved.name,saved.fatherName,saved.motherName,saved.guardianName,saved.phone,saved.active,sc!=null?new ClassRef(sc.id,sc.name,sc.section):null);}

  @GetMapping("/fee-types") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") List<FeeType> listTypes(Authentication a,@RequestParam(required=false) Long schoolId){return types.findBySchoolIdAndActiveTrue(school(a,schoolId));}
  @PostMapping("/fee-types") @PreAuthorize("hasRole('SCHOOL_ADMIN')") @ResponseStatus(HttpStatus.CREATED) FeeType createType(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody FeeTypeRequest q){FeeType f=new FeeType();f.school=schools.getReferenceById(school(a,schoolId));f.code=q.code().toUpperCase(Locale.ROOT);f.displayName=q.displayName();f.active=q.active()==null||q.active();return types.save(f);}

  @GetMapping("/fee-structure") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") List<FeeStructureResponse> feeStructure(Authentication a,@RequestParam String academicYear,@RequestParam(required=false) Long schoolId){return feeStructures.list(a,academicYear);}
  @PutMapping("/fee-structure") @PreAuthorize("hasRole('SCHOOL_ADMIN')") FeeStructureResponse updateFeeStructure(Authentication a,@Valid @RequestBody FeeStructureItemRequest q){return feeStructures.upsert(a,q);}

  @GetMapping("/users") @PreAuthorize("hasRole('SCHOOL_ADMIN')") List<UserResponse> users(Authentication a){return userManagement.listSchoolUsers(a);}
  @PostMapping("/users") @PreAuthorize("hasRole('SCHOOL_ADMIN')") @ResponseStatus(HttpStatus.CREATED) UserResponse createUser(Authentication a,@Valid @RequestBody CreateUserRequest q){return userManagement.createSchoolUser(a,q);}
  @PatchMapping("/users/{id}") @PreAuthorize("hasRole('SCHOOL_ADMIN')") UserResponse setUserActive(Authentication a,@PathVariable Long id,@RequestParam boolean active){return userManagement.setSchoolUserActive(a,id,active);}

  @GetMapping("/fees") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") List<ReceiptResponse> listReceipts(Authentication a,@RequestParam(required=false) Long schoolId){return receipts.findBySchoolIdOrderByCreatedAtDesc(school(a,schoolId)).stream().map(fees::view).toList();}
  @GetMapping("/fees/{id}") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") ReceiptResponse receipt(Authentication a,@RequestParam(required=false) Long schoolId,@PathVariable Long id){return fees.view(receipts.findByIdAndSchoolId(id,school(a,schoolId)).orElseThrow(()->new NoSuchElementException("Receipt not found")));}
  @PostMapping("/fees") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") @ResponseStatus(HttpStatus.CREATED) ReceiptResponse createFee(Authentication a,@RequestParam(required=false) Long schoolId,@Valid @RequestBody CreateFeeRequest q){return fees.create(a,schoolId,q);}
  @PostMapping("/fees/{id}/cancel") @PreAuthorize("hasRole('SCHOOL_ADMIN')") ReceiptResponse cancel(Authentication a,@RequestParam(required=false) Long schoolId,@PathVariable Long id,@Valid @RequestBody CancelRequest q){return fees.cancel(a,id,q,schoolId);}

  @GetMapping("/reports/fees/monthly") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") ReportResponse monthly(Authentication a,@RequestParam(required=false) Long schoolId,@RequestParam @Min(2000) int year,@RequestParam @Min(1) @Max(12) int month,@RequestParam(required=false) Long classId){Long sid=school(a,schoolId);LocalDate start=LocalDate.of(year,month,1),end=start.plusMonths(1);return report(sid,start,end,classId,String.format("%d-%02d",year,month));}
  @GetMapping("/reports/fees/yearly") @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','SCHOOL_USER')") ReportResponse yearly(Authentication a,@RequestParam(required=false) Long schoolId,@RequestParam @Min(2000) int startYear,@RequestParam(required=false) Long classId){Long sid=school(a,schoolId);LocalDate start=LocalDate.of(startYear,4,1);return report(sid,start,start.plusYears(1),classId,startYear+"-"+(startYear+1));}
  private ReportResponse report(Long sid,LocalDate start,LocalDate end,Long classId,String period){List<Breakdown>b=receipts.totals(sid,start,end,classId).stream().map(x->new Breakdown((String)x[0],(String)x[1],(BigDecimal)x[2])).toList();return new ReportResponse(period,b,b.stream().map(Breakdown::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String,String>> denied(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("code", "ACCESS_DENIED", "message", e.getMessage() == null ? "Access denied" : e.getMessage()));
  }

  @ExceptionHandler({NoSuchElementException.class,IllegalArgumentException.class,IllegalStateException.class,org.springframework.dao.DataIntegrityViolationException.class})
  ResponseEntity<Map<String,String>> bad(RuntimeException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code",e.getClass().getSimpleName(),"message",e.getMessage()==null?"Request failed":e.getMessage()));
  }
}
