package org.uklin.plugin.api.yapi.model;

import org.eclipse.xtend.lib.annotations.Data;

@Data
public class YApiMock {
	private Object mock;

	public YApiMock(Object value) {
		this.mock=value;
	}
}
