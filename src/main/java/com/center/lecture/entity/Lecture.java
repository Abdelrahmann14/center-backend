package com.center.lecture.entity;
import com.center.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lectures", indexes = @Index(name = "lectures_grade_idx", columnList = "grade"))
@Getter
@Setter
@NoArgsConstructor
public class Lecture extends TenantEntity {

    @Column(nullable = false)
    private String name;

    private String grade;

    @Column(name = "exam_name")
    private String examName;

    /** Free text ("50", "50 درجة"); the exam-score cap is its first number. */
    @Column(name = "exam_grade")
    private String examGrade;

    private String homework;
}
