package com.school;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

record FeeStructureItemRequest(@NotNull Long classId, @NotNull Long feeTypeId,
    @NotBlank @Size(min=4,max=9) String academicYear,
    @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal amount) {}

record FeeStructureResponse(Long id, Long classId, String className, String section,
    Long feeTypeId, String feeTypeCode, String feeTypeName, String academicYear,
    BigDecimal amount, boolean active) {}

@Service
class FeeStructureService {
  private final ClassRepository classes;
  private final FeeTypeRepository types; private final ClassFeeStructureRepository structures;
  private final AuthorizationService authorization;

  FeeStructureService(ClassRepository classes, FeeTypeRepository types, ClassFeeStructureRepository structures, AuthorizationService authorization) {
    this.classes=classes; this.types=types; this.structures=structures; this.authorization=authorization;
  }

  List<FeeStructureResponse> list(Authentication auth, String academicYear) {
    AppUser u=authorization.actor(auth); authorization.requireSchoolStaff(u); Long sid=u.school.id;
    return structures.findBySchoolIdAndAcademicYearAndActiveTrueOrderByClassAndFeeType(sid, academicYear).stream().map(this::view).toList();
  }

  @Transactional
  FeeStructureResponse upsert(Authentication auth, @Valid FeeStructureItemRequest q) {
    AppUser u=authorization.actor(auth); authorization.requireSchoolAdmin(u); Long sid=u.school.id;
    SchoolClass c=classes.findByIdAndSchoolId(q.classId(),sid).orElseThrow(() -> new NoSuchElementException("Class not found"));
    FeeType t=types.findByIdAndSchoolIdAndActiveTrue(q.feeTypeId(),sid).orElseThrow(() -> new NoSuchElementException("Fee type not found"));
    ClassFeeStructure s=structures.findBySchoolIdAndSchoolClass_IdAndFeeType_IdAndAcademicYear(sid,q.classId(),q.feeTypeId(),q.academicYear()).orElseGet(ClassFeeStructure::new);
    s.school=u.school; s.schoolClass=c; s.feeType=t; s.academicYear=q.academicYear(); s.amount=q.amount(); s.active=true;
    return view(structures.save(s));
  }

  private FeeStructureResponse view(ClassFeeStructure s) { return new FeeStructureResponse(s.id,s.schoolClass.id,s.schoolClass.name,s.schoolClass.section,s.feeType.id,s.feeType.code,s.feeType.displayName,s.academicYear,s.amount,s.active); }
}
