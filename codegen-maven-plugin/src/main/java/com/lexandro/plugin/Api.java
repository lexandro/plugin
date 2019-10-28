package com.lexandro.plugin;

import java.util.List;
import lombok.Data;

@Data
public class Api {

    private String name;
    private String formatVersion;
    private String description;
    private String version;
    private List<Entity> entities;

}
