package com.school;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

<<<<<<< HEAD
interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByUsernameAndActiveTrue(String username);
}

interface SchoolRepository extends JpaRepository<School, Long> {}

interface ClassRepository extends JpaRepository<SchoolClass, Long> {
  List<SchoolClass> findBySchoolId(Long schoolId);

  Optional<SchoolClass> findByIdAndSchoolId(Long id, Long schoolId);
}

interface StudentRepository extends JpaRepository<Student, Long> {
  List<Student> findBySchoolId(Long schoolId);

  Optional<Student> findByIdAndSchoolId(Long id, Long schoolId);
}

interface FeeTypeRepository extends JpaRepository<FeeType, Long> {
  List<FeeType> findBySchoolIdAndActiveTrue(Long schoolId);

  Optional<FeeType> findByIdAndSchoolIdAndActiveTrue(Long id, Long schoolId);
}

interface ReceiptRepository extends JpaRepository<FeeReceipt, Long> {
  Optional<FeeReceipt> findByIdAndSchoolId(Long id, Long schoolId);

  List<FeeReceipt> findBySchoolIdOrderByCreatedAtDesc(Long schoolId);

  @Query(
      "select i.feeType.code, i.feeType.displayName, sum(i.amount) from FeeReceipt r join r.items i where r.school.id=:schoolId and r.status='ACTIVE' and r.paymentDate>=:start and r.paymentDate<:end and (:classId is null or r.student.schoolClass.id=:classId) group by i.feeType.code,i.feeType.displayName")
  List<Object[]> totals(
      @Param("schoolId") Long schoolId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("classId") Long classId);
}

interface AuditRepository extends JpaRepository<AuditLog, Long> {}
=======
/** Looks up active accounts for authentication and authorization. */
interface UserRepository extends JpaRepository<AppUser,Long> { Optional<AppUser> findByUsernameAndActiveTrue(String username); }
/** Persists school tenancy roots. */
interface SchoolRepository extends JpaRepository<School,Long> {}
/** Provides school-scoped class lookups. */
interface ClassRepository extends JpaRepository<SchoolClass,Long> { List<SchoolClass> findBySchoolId(Long schoolId); Optional<SchoolClass> findByIdAndSchoolId(Long id,Long schoolId); }
/** Provides school-scoped student lookups. */
interface StudentRepository extends JpaRepository<Student,Long> { List<Student> findBySchoolId(Long schoolId); Optional<Student> findByIdAndSchoolId(Long id,Long schoolId); }
/** Provides active, school-scoped fee-type lookups. */
interface FeeTypeRepository extends JpaRepository<FeeType,Long> { List<FeeType> findBySchoolIdAndActiveTrue(Long schoolId); Optional<FeeType> findByIdAndSchoolIdAndActiveTrue(Long id,Long schoolId); }
/** Retrieves receipts and aggregation data used by collection reports. */
interface ReceiptRepository extends JpaRepository<FeeReceipt,Long> {
  Optional<FeeReceipt> findByIdAndSchoolId(Long id,Long schoolId); List<FeeReceipt> findBySchoolIdOrderByCreatedAtDesc(Long schoolId);
  @Query("select i.feeType.code, i.feeType.displayName, sum(i.amount) from FeeReceipt r join r.items i where r.school.id=:schoolId and r.status='ACTIVE' and r.paymentDate>=:start and r.paymentDate<:end and (:classId is null or r.student.schoolClass.id=:classId) group by i.feeType.code,i.feeType.displayName")
  List<Object[]> totals(@Param("schoolId") Long schoolId,@Param("start") LocalDate start,@Param("end") LocalDate end,@Param("classId") Long classId);
}
/** Persists append-only audit events. */
interface AuditRepository extends JpaRepository<AuditLog,Long> {}
>>>>>>> 76d68d7 (Document application classes)
