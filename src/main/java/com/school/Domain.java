package com.school;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

enum Role { ADMIN, SCHOOL }
enum ReceiptStatus { ACTIVE, CANCELLED }

@MappedSuperclass abstract class Timestamped {
  @Column(nullable=false, updatable=false) Instant createdAt;
  @Column(nullable=false) Instant updatedAt;
  @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
  @PreUpdate void updated() { updatedAt = Instant.now(); }
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="schools") class School extends Timestamped {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false) String name; String address; String phone; String email; boolean active=true;
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="users") class AppUser extends Timestamped {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false, unique=true) String username; @Column(name="password_hash",nullable=false) String passwordHash;
  @Enumerated(EnumType.STRING) @Column(nullable=false) Role role; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") School school; boolean active=true;
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="classes") class SchoolClass extends Timestamped {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id",nullable=false) School school;
  @Column(nullable=false) String name; @Column(nullable=false) String section; boolean active=true;
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="students") class Student extends Timestamped {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id",nullable=false) School school;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="class_id",nullable=false) SchoolClass schoolClass; @Column(name="admission_number",nullable=false) String admissionNumber;
  @Column(nullable=false) String name; String fatherName; String motherName; String guardianName; String phone; boolean active=true;
}
@Entity @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY) @Table(name="fee_types") class FeeType {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id",nullable=false) School school;
  @Column(nullable=false) String code; @Column(name="display_name",nullable=false) String displayName; boolean active=true;
}
@Entity @Table(name="fee_receipts") class FeeReceipt {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id",nullable=false) School school;
  @Column(name="receipt_number",nullable=false,unique=true) String receiptNumber; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id",nullable=false) Student student;
  @Column(name="total_amount",nullable=false,precision=12,scale=2) BigDecimal totalAmount; @Column(name="payment_date",nullable=false) LocalDate paymentDate;
  @Enumerated(EnumType.STRING) @Column(nullable=false) ReceiptStatus status=ReceiptStatus.ACTIVE; String cancellationReason; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cancelled_by") AppUser cancelledBy; Instant cancelledAt;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by",nullable=false) AppUser createdBy; @Column(nullable=false,updatable=false) Instant createdAt=Instant.now();
  @OneToMany(mappedBy="receipt",cascade=CascadeType.ALL,orphanRemoval=true) List<FeeReceiptItem> items=new ArrayList<>();
}
@Entity @Table(name="fee_receipt_items") class FeeReceiptItem {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="receipt_id",nullable=false) FeeReceipt receipt;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="fee_type_id",nullable=false) FeeType feeType; @Column(nullable=false,precision=12,scale=2) BigDecimal amount;
}
@Entity @Table(name="audit_logs") class AuditLog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id") School school; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") AppUser user;
  @Column(nullable=false) String action; @Column(nullable=false) String entityType; @Column(nullable=false) Long entityId; @Column(columnDefinition="jsonb") String oldValue; @Column(columnDefinition="jsonb") String newValue; @Column(nullable=false) Instant createdAt=Instant.now();
}
