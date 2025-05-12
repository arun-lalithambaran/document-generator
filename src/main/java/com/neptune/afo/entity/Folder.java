package com.neptune.afo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "folder")
@Data
@Getter
@Setter
public class Folder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long folderId;
    private String folderName;
    @ManyToOne
    @JoinColumn(name = "createdBy")
    private User createdBy;
    @OneToMany(mappedBy = "")
    private List<UserFile> files;
    private Instant createdAt;
    private Boolean status;
}
