package org.uklin.plugin.api.yapi.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class YApiBody {
	private String type;
	private String description;
	private YApiMock mock;
	private String defaultValue;
	private Map<String, YApiBody> properties  = new LinkedHashMap<>();
	private YApiBody items;
	private List<String> required = new ArrayList<>();
}
