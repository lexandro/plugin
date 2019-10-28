package com.lexandro.plugin;

import java.util.List;
import lombok.Data;

@Data
public class Entity {

    private String name;
    private List<Field> fields;
}
