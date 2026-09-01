package com.school;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsernameAndActiveTrue(String username);

  Optional<AppUser> findByUsername(String username);

  List<AppUser> findBySchoolIdOrderByUsername(Long schoolId);

  List<AppUser> findBySchoolIdAndRoleOrderByUsername(Long schoolId, Role role);
}

interface SchoolRepository extends JpaRepository<School, Long> {
  List<School> findByOrderByName();
}

interface ClassRepository extends JpaRepository<SchoolClass, Long> {
  List<SchoolClass> findBySchoolId(Long schoolId);

  List<SchoolClass> findBySchoolIdAndActiveTrue(Long schoolId);

  @Query(
      "select c from SchoolClass c where c.school.id = :schoolId and c.active = true order by case lower(c.name) when 'nursery' then 0 when 'lkg' then 1 when 'ukg' then 2 when '1' then 3 when '2' then 4 when '3' then 5 when '4' then 6 when '5' then 7 when '6' then 8 when '7' then 9 when '8' then 10 when '9' then 11 when '10' then 12 when '11' then 13 when '12' then 14 else 1000 end, c.section")
  List<SchoolClass> findBySchoolIdOrderByNameAscSectionAsc(@Param("schoolId") Long schoolId);

  Optional<SchoolClass> findByIdAndSchoolId(Long id, Long schoolId);
}

interface StudentRepository extends JpaRepository<Student, Long> {
  @Query("select s from Student s join fetch s.schoolClass where s.school.id = :schoolId")
  List<Student> findBySchoolId(@Param("schoolId") Long schoolId);

  Optional<Student> findByIdAndSchoolId(Long id, Long schoolId);

  Optional<Student> findBySchoolIdAndAdmissionNumber(Long schoolId, String admissionNumber);
}

interface FeeTypeRepository extends JpaRepository<FeeType, Long> {
  List<FeeType> findBySchoolIdAndActiveTrue(Long schoolId);

  Optional<FeeType> findByIdAndSchoolIdAndActiveTrue(Long id, Long schoolId);
}

interface ClassFeeStructureRepository extends JpaRepository<ClassFeeStructure, Long> {
  @Query(
      "select c from ClassFeeStructure c "
          + "where c.school.id = :schoolId and c.academicYear = :academicYear and c.active = true "
          + "order by c.schoolClass.id asc, c.feeType.id asc")
  List<ClassFeeStructure> findBySchoolIdAndAcademicYearAndActiveTrueOrderByClassAndFeeType(
      @Param("schoolId") Long schoolId, @Param("academicYear") String academicYear);

  List<ClassFeeStructure> findBySchoolIdAndSchoolClass_IdAndAcademicYearAndActiveTrue(
      Long schoolId, Long classId, String academicYear);

  Optional<ClassFeeStructure> findBySchoolIdAndSchoolClass_IdAndFeeType_IdAndAcademicYear(
      Long schoolId, Long classId, Long feeTypeId, String academicYear);
}

interface ReceiptRepository extends JpaRepository<FeeReceipt, Long> {
  @Query(
      "select r from FeeReceipt r "
          + "left join fetch r.items i "
          + "left join fetch i.feeType ft "
          + "left join fetch r.student s "
          + "left join fetch s.schoolClass sc "
          + "where r.id = :id and r.school.id = :schoolId")
  Optional<FeeReceipt> findByIdAndSchoolId(@Param("id") Long id, @Param("schoolId") Long schoolId);

  @Query(
      "select distinct r from FeeReceipt r "
          + "left join fetch r.items i "
          + "left join fetch i.feeType ft "
          + "left join fetch r.student s "
          + "left join fetch s.schoolClass sc "
          + "where r.school.id = :schoolId order by r.createdAt desc")
  List<FeeReceipt> findBySchoolIdOrderByCreatedAtDesc(@Param("schoolId") Long schoolId);

  @Query(
      "select i.feeType.code, i.feeType.displayName, sum(i.amount) from FeeReceipt r join r.items i where r.school.id=:schoolId and r.status = com.school.ReceiptStatus.ACTIVE and r.paymentDate>=:start and r.paymentDate<:end and (:classId is null or r.student.schoolClass.id=:classId) group by i.feeType.code,i.feeType.displayName")
  List<Object[]> totals(
      @Param("schoolId") Long schoolId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("classId") Long classId);
}

interface AuditRepository extends JpaRepository<AuditLog, Long> {}
