package com.neptune.afo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "publicResource")
@Getter
@Setter
@NoArgsConstructor
public class PublicResourceData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    private String responseData;

    private Date createdOn;
}
