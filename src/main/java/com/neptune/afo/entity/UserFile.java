package com.neptune.afo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "files")
@Data
@Getter
@Setter
public class UserFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;
    private String fileName;
    private String extension;
    private Long fileSize;
    private String filePath;
    @ManyToOne
    @JoinColumn(name = "createdBy")
    private User createdBy;
    private Instant createdAt;
    private Boolean status;
}
