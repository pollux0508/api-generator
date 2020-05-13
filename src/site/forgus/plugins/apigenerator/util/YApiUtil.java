package site.forgus.plugins.apigenerator.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import site.forgus.plugins.apigenerator.normal.FieldInfo;
import site.forgus.plugins.apigenerator.yapi.model.YApiBody;
import site.forgus.plugins.apigenerator.yapi.model.YApiMock;

import java.lang.reflect.Modifier;
import java.util.Objects;


public class YApiUtil {
	private static final Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC, Modifier.FINAL).setPrettyPrinting().create();

	public static YApiBody process(FieldInfo fieldInfo) {
		YApiBody result = new YApiBody();
		switch (fieldInfo.getParamType()) {
			case OBJECT:
				processObject(result,fieldInfo);
				break;
			case ARRAY:
				processArray(result,fieldInfo);
				break;
			default:
				String desc = fieldInfo.getDesc().trim();
				result.setType(fieldInfo.getPsiType().getPresentableText());
				result.setDescription(desc);
				result.setMock(new YApiMock(FieldUtil.getValue(fieldInfo.getPsiType())));
		}
		return result;
	}

	private static void processObject(YApiBody result,FieldInfo fieldInfo){
		result.setType("object");
		result.setDescription(fieldInfo.getDesc().trim());
		if(fieldInfo.getChildren()!=null) {
			for (FieldInfo bean : fieldInfo.getChildren()) {
				result.getProperties().put(bean.getName(), process(bean));
				if (bean.isRequire()) {
					result.getRequired().add(bean.getName());
				}
			}
		}
	}

	private static void processArray(YApiBody result,FieldInfo fieldInfo){
		result.setType("array");
		result.setDescription(fieldInfo.getDesc().trim());
		result.setItems(new YApiBody());
		if(fieldInfo.getChildren()!=null){
			for(FieldInfo bean: fieldInfo.getChildren()){
				result.getItems().getProperties().put(bean.getName(),process(bean));
				if(bean.isRequire()) {
					result.getItems().getRequired().add(bean.getName());
				}
			}
		}

	}

	public static String buildJson5(FieldInfo fieldInfo) {
		return gson.toJson(YApiUtil.process(fieldInfo));
	}

	public static String getFirstAndLastChar(PsiMethod psiMethodTarget) {
		return getFirstAndLastChar(Objects.requireNonNull(psiMethodTarget.getDocComment()));
	}


	public static String getFirstAndLastChar(PsiDocComment psiDocComment) {
		return DesUtil.trimFirstAndLastChar(
				psiDocComment.getText().split("@")[0]
						.replace("@description", "")
						.replace("@Description", "")
						.replace("Description", "")
						.replace("<br>", "\n")
						.replace(":", "")
						.replace("*", "")
						.replace("/", "")
						.replace("<p>", "\n")
						.replace("</p>", "\n")
						.replace("<li>", "\n")
						.replace("</li>", "\n")
						.replace("{", ""), ' '
		).trim();
	}
}
