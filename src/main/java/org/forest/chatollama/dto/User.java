package org.forest.chatollama.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class User implements Serializable {
    private Long id;
    private Long schoolId;
    private String username;
    private String name;
    private Integer type;
    private Long schoolDictId;
}