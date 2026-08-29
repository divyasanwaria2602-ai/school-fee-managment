package com.school;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsernameAndActiveTrue(String username);
}

interface SchoolRepository extends JpaRepository<School, Long> {}

interface ClassRepository extends JpaRepository<SchoolClass, Long> {
  List<SchoolClass> findBySchoolId(Long schoolId);

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

interface ReceiptRepository extends JpaRepository<FeeReceipt, Long> {
  @Query("select r from FeeReceipt r "
      + "left join fetch r.items i "
      + "left join fetch i.feeType ft "
      + "left join fetch r.student s "
      + "left join fetch s.schoolClass sc "
      + "where r.id = :id and r.school.id = :schoolId")
  Optional<FeeReceipt> findByIdAndSchoolId(@Param("id") Long id, @Param("schoolId") Long schoolId);

  @Query("select distinct r from FeeReceipt r "
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
