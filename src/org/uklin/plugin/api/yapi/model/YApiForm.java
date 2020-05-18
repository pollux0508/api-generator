package org.uklin.plugin.api.yapi.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class YApiForm extends YApiParam {
    private static final long serialVersionUID = 259883183902353577L;

    private String type = "text";

}
