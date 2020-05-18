package org.uklin.plugin.api.util;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.uklin.plugin.api.config.ApiGeneratorConfig;
import org.uklin.plugin.api.constant.TypeEnum;
import org.uklin.plugin.api.constant.WebAnnotation;
import org.uklin.plugin.api.normal.FieldInfo;
import org.uklin.plugin.api.normal.MethodInfo;
import org.uklin.plugin.api.yapi.enums.RequestBodyTypeEnum;
import org.uklin.plugin.api.yapi.enums.RequestMethodEnum;
import org.uklin.plugin.api.yapi.enums.ResponseBodyTypeEnum;
import org.uklin.plugin.api.yapi.model.*;
import org.uklin.plugin.api.yapi.sdk.YApiSdk;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;


public class YApiUtil {
	private static final String SLASH = "/";

	protected static ApiGeneratorConfig config;

	private static final Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC, Modifier.FINAL).setPrettyPrinting().create();

	public static void initConfig(Project project){
		config = ServiceManager.getService(project, ApiGeneratorConfig.class);
	}

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

	public static String getParamDesc(String tagText) {
		String[] strings = tagText.replace("*","").replaceAll(" {2,}", " ").trim().split(" ");
		if (strings.length >= 3) {
			String desc = strings[2];
			return desc.replace("\n", "");
		}
		return "";
	}

	public static  void uploadApiToYApi(Project project, PsiElement referenceAt, PsiClass selectedClass) {
		PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(referenceAt, PsiMethod.class);
		if (selectedMethod != null) {
			try {
				uploadSelectedMethodToYApi(project, selectedMethod);
			} catch (IOException e) {
				NotificationUtil.errorNotify(e.getMessage(), project);
			}
			return;
		}
		try {
			uploadHttpMethodsToYApi(project, selectedClass);
		} catch (IOException e) {
			NotificationUtil.errorNotify(e.getMessage(), project);
		}
	}

	private static void uploadHttpMethodsToYApi(Project project, PsiClass psiClass) throws IOException {
		if (!haveControllerAnnotation(psiClass)) {
			NotificationUtil.warnNotify("Upload api failed, reason:\n not REST api.", project);
			return;
		}
		if (StringUtils.isEmpty(config.getState().yApiServerUrl)) {
			String serverUrl = Messages.showInputDialog("Input YApi Server Url", "YApi Server Url", Messages.getInformationIcon());
			if (StringUtils.isEmpty(serverUrl)) {
				NotificationUtil.warnNotify("YApi server url can not be empty.", project);
				return;
			}
			config.getState().yApiServerUrl = serverUrl;
		}
		if (StringUtils.isEmpty(config.getState().projectToken)) {
			String projectToken = Messages.showInputDialog("Input Project Token", "Project Token", Messages.getInformationIcon());
			if (StringUtils.isEmpty(projectToken)) {
				NotificationUtil.warnNotify("Project token can not be empty.", project);
				return;
			}
			config.getState().projectToken = projectToken;
		}
		if (StringUtils.isEmpty(Objects.requireNonNull(config.getState()).projectId)) {
			YApiProject projectInfo = YApiSdk.getProjectInfo(Objects.requireNonNull(config.getState()).yApiServerUrl, config.getState().projectToken);
			String projectId = projectInfo.get_id() == null ? Messages.showInputDialog("Input Project Id", "Project Id", Messages.getInformationIcon()) : projectInfo.get_id().toString();
			if (StringUtils.isEmpty(projectId)) {
				NotificationUtil.warnNotify("Project id can not be empty.", project);
				return;
			}
			config.getState().projectId = projectId;
		}
		PsiMethod[] methods = psiClass.getMethods();
		boolean uploadSuccess = false;
		for (PsiMethod method : methods) {
			if (hasMappingAnnotation(method)) {
				uploadToYApi(project, method);
				uploadSuccess = true;
			}
		}
		if (uploadSuccess) {
			NotificationUtil.infoNotify("Upload api success.", project);
			return;
		}
		NotificationUtil.infoNotify("Upload api failed, reason:\n not REST api.", project);
	}

	public static void generateMarkdownForInterface(Project project, PsiElement referenceAt, PsiClass selectedClass) {
		PsiMethod selectedMethod = PsiTreeUtil.getContextOfType(referenceAt, PsiMethod.class);
		if (selectedMethod != null) {
			try {
				generateMarkdownForSelectedMethod(project, selectedMethod);
			} catch (IOException e) {
				NotificationUtil.errorNotify(e.getMessage(), project);
			}
			return;
		}
		try {
			generateMarkdownsForAllMethods(project, selectedClass);
		} catch (IOException e) {
			NotificationUtil.errorNotify(e.getMessage(), project);
		}
	}

	public static void generateMarkdownForClass(Project project, PsiClass psiClass) {
		String dirPath = getDirPath(project);
		if (!mkDirectory(project, dirPath)) {
			return;
		}
		boolean generateSuccess = false;
		try {
			generateSuccess = generateDocForClass(project, psiClass, dirPath);
		} catch (IOException e) {
			NotificationUtil.errorNotify(e.getMessage(), project);
		}
		if(generateSuccess) {
			NotificationUtil.infoNotify("generate api doc success.", project);
		}
	}

	protected static void generateMarkdownForSelectedMethod(Project project, PsiMethod selectedMethod) throws IOException {
		String dirPath = getDirPath(project);
		if (!mkDirectory(project, dirPath)) {
			return;
		}
		boolean generateSuccess = generateDocForMethod(project, selectedMethod, dirPath);
		if(generateSuccess) {
			NotificationUtil.infoNotify("generate api doc success.", project);
		}
	}

	protected static void generateMarkdownsForAllMethods(Project project, PsiClass selectedClass) throws IOException {
		String dirPath = getDirPath(project);
		if (!mkDirectory(project, dirPath)) {
			return;
		}
		boolean generateSuccess = false;
		for (PsiMethod psiMethod : selectedClass.getMethods()) {
			if(generateDocForMethod(project, psiMethod, dirPath)) {
				generateSuccess = true;
			}
		}
		if(generateSuccess) {
			NotificationUtil.infoNotify("generate api doc success.", project);
		}
	}

	private static void uploadSelectedMethodToYApi(Project project, PsiMethod method) throws IOException {
		if (!hasMappingAnnotation(method)) {
			NotificationUtil.warnNotify("Upload api failed, reason:\n not REST api.", project);
			return;
		}
		if (StringUtils.isEmpty(Objects.requireNonNull(config.getState()).yApiServerUrl)) {
			String serverUrl = Messages.showInputDialog("Input YApi Server Url", "YApi Server Url", Messages.getInformationIcon());
			if (StringUtils.isEmpty(serverUrl)) {
				NotificationUtil.warnNotify("YApi server url can not be empty.", project);
				return;
			}
			config.getState().yApiServerUrl = serverUrl;
		}
		if (StringUtils.isEmpty(Objects.requireNonNull(config.getState()).projectToken)) {
			String projectToken = Messages.showInputDialog("Input Project Token", "Project Token", Messages.getInformationIcon());
			if (StringUtils.isEmpty(projectToken)) {
				NotificationUtil.warnNotify("Project token can not be empty.", project);
				return;
			}
			config.getState().projectToken = projectToken;
		}
		if (StringUtils.isEmpty(config.getState().projectId)) {
			YApiProject projectInfo = YApiSdk.getProjectInfo(config.getState().yApiServerUrl, config.getState().projectToken);
			String projectId = projectInfo.get_id() == null ? Messages.showInputDialog("Input Project Id", "Project Id", Messages.getInformationIcon()) : projectInfo.get_id().toString();
			if (StringUtils.isEmpty(projectId)) {
				NotificationUtil.warnNotify("Project id can not be empty.", project);
				return;
			}
			config.getState().projectId = projectId;
		}
		uploadToYApi(project, method);
	}

	private static void uploadToYApi(Project project, PsiMethod psiMethod) throws IOException {
		YApiInterface yApiInterface = buildYApiInterface(project, psiMethod);
		if (yApiInterface == null) {
			return;
		}
		YApiResponse yApiResponse = YApiSdk.saveInterface(Objects.requireNonNull(config.getState()).yApiServerUrl, yApiInterface);
		if (yApiResponse.getErrcode() != 0) {
			NotificationUtil.errorNotify("Upload api failed, cause:" + yApiResponse.getErrmsg(), project);
			return;
		}
		NotificationUtil.infoNotify("Upload api success.", project);
	}

	private static YApiInterface buildYApiInterface(Project project, PsiMethod psiMethod) throws IOException {
		PsiClass containingClass = psiMethod.getContainingClass();
		if (containingClass == null) {
			return null;
		}
		PsiAnnotation controller = null;
		PsiAnnotation classRequestMapping = null;
		for (PsiAnnotation annotation : containingClass.getAnnotations()) {
			String text = annotation.getText();
			if (text.endsWith(WebAnnotation.Controller)) {
				controller = annotation;
			} else if (text.contains(WebAnnotation.RequestMapping)) {
				classRequestMapping = annotation;
			}
		}
		if (controller == null) {
			NotificationUtil.warnNotify("Invalid Class File!", project);
			return null;
		}
		MethodInfo methodInfo = new MethodInfo(psiMethod);
		PsiAnnotation methodMapping = getMethodMapping(psiMethod);
		YApiInterface yApiInterface = new YApiInterface();
		yApiInterface.setToken(Objects.requireNonNull(config.getState()).projectToken);
		assert methodMapping != null;
		RequestMethodEnum requestMethodEnum = getMethodFromAnnotation(methodMapping);
		yApiInterface.setMethod(requestMethodEnum.name());
		if (methodInfo.getParamStr().contains(WebAnnotation.RequestBody)) {
			yApiInterface.setReq_body_type(RequestBodyTypeEnum.JSON.getValue());
			yApiInterface.setReq_body_other(YApiUtil.buildJson5(getRequestBodyParam(methodInfo.getRequestFields())));
			yApiInterface.setReq_body_is_json_schema(true);
		} else {
			if (yApiInterface.getMethod().equals(RequestMethodEnum.POST.name())) {
				yApiInterface.setReq_body_type(RequestBodyTypeEnum.FORM.getValue());
				yApiInterface.setReq_body_form(listYApiForms(methodInfo.getRequestFields()));
			}
		}
		yApiInterface.setReq_query(listYApiQueries(methodInfo.getRequestFields(), requestMethodEnum));
		Map<String, YApiCat> catNameMap = getCatNameMap();
		PsiDocComment classDesc = containingClass.getDocComment();
		yApiInterface.setCatid(getCatId(catNameMap, classDesc));
		//修改了接口命名方式
		String title = methodInfo.getDesc().split("\n")[0].trim();
		yApiInterface.setTitle(title);
		yApiInterface.setPath(buildPath(classRequestMapping, methodMapping));
		if (containResponseBodyAnnotation(psiMethod.getAnnotations()) || controller.getText().contains("Rest")) {
			yApiInterface.setReq_headers(Collections.singletonList(YApiHeader.json()));
			yApiInterface.setRes_body(YApiUtil.buildJson5(methodInfo.getResponse()));
			yApiInterface.setRes_body_is_json_schema(true);
		} else {
			yApiInterface.setReq_headers(Collections.singletonList(YApiHeader.form()));
			yApiInterface.setRes_body_type(ResponseBodyTypeEnum.RAW.getValue());
			yApiInterface.setRes_body("");
		}
		yApiInterface.setReq_params(listYApiPathVariables(methodInfo.getRequestFields()));
		String desc = methodInfo.getDesc().replace(title, "").trim();
		yApiInterface.setDesc(org.apache.commons.lang3.StringUtils.isNotBlank(desc) ? desc : "<pre><code data-language=\"java\" class=\"java\">" + getMethodDesc(psiMethod) + "</code> </pre>");
		yApiInterface.setMarkdown(yApiInterface.getDesc().trim());
		return yApiInterface;
	}

	private static String buildPath(PsiAnnotation classRequestMapping, PsiAnnotation methodMapping) {
		String classPath = getPathFromAnnotation(classRequestMapping);
		String methodPath = getPathFromAnnotation(methodMapping);
		return classPath + methodPath;
	}

	private static FieldInfo getRequestBodyParam(List<FieldInfo> params) {
		if (params == null) {
			return null;
		}
		for (FieldInfo fieldInfo : params) {
			if (FieldUtil.findAnnotationByName(fieldInfo.getAnnotations(), WebAnnotation.RequestBody) != null) {
				return fieldInfo;
			}
		}
		return null;
	}

	private static boolean containResponseBodyAnnotation(PsiAnnotation[] annotations) {
		for (PsiAnnotation annotation : annotations) {
			if (annotation.getText().contains(WebAnnotation.ResponseBody)) {
				return true;
			}
		}
		return false;
	}

	private static String getMethodDesc(PsiMethod psiMethod) {
		String methodDesc = psiMethod.getText().replace(Objects.nonNull(psiMethod.getBody()) ? psiMethod.getBody().getText() : "", "");
		if (!Strings.isNullOrEmpty(methodDesc)) {
			methodDesc = methodDesc.replace("<", "&lt;").replace(">", "&gt;");
		}
		return methodDesc;
	}

	private static List<YApiPathVariable> listYApiPathVariables(List<FieldInfo> requestFields) {
		List<YApiPathVariable> yApiPathVariables = new ArrayList<>();
		for (FieldInfo fieldInfo : requestFields) {
			List<PsiAnnotation> annotations = fieldInfo.getAnnotations();
			PsiAnnotation pathVariable = getPathVariableAnnotation(annotations);
			if(pathVariable == null) {
				continue;
			}
			YApiPathVariable yApiPathVariable = new YApiPathVariable();
			yApiPathVariable.setName(getPathVariableName(pathVariable,fieldInfo.getName()));
			yApiPathVariable.setDesc(fieldInfo.getDesc());
			yApiPathVariable.setExample(Objects.requireNonNull(FieldUtil.getValue(fieldInfo.getPsiType())).toString());
			yApiPathVariables.add(yApiPathVariable);
		}
		return yApiPathVariables;
	}

	private static String getPathVariableName(PsiAnnotation pathVariable,String fieldName) {
		PsiNameValuePair[] psiNameValuePairs = pathVariable.getParameterList().getAttributes();
		if (psiNameValuePairs.length > 0) {
			for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
				String literalValue = psiNameValuePair.getLiteralValue();
				if (StringUtils.isEmpty(literalValue)) {
					continue;
				}
				String name = psiNameValuePair.getName();
				if (name == null || "value".equals(name) || "name".equals(name)) {
					return literalValue;
				}
			}
		}
		return fieldName;
	}

	private static PsiAnnotation getPathVariableAnnotation(List<PsiAnnotation> annotations) {
		return FieldUtil.findAnnotationByName(annotations, WebAnnotation.PathVariable);
	}


	private static String getPathFromAnnotation(PsiAnnotation annotation) {
		if (annotation == null) {
			return "";
		}
		PsiNameValuePair[] psiNameValuePairs = annotation.getParameterList().getAttributes();
		if (psiNameValuePairs.length == 1 && psiNameValuePairs[0].getName() == null) {
			return appendSlash(psiNameValuePairs[0].getLiteralValue());
		}
		if (psiNameValuePairs.length >= 1) {
			for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
				if ("value".equals(psiNameValuePair.getName()) || "path".equals(psiNameValuePair.getName())) {
					return appendSlash(psiNameValuePair.getLiteralValue());
				}
			}
		}
		return "";
	}

	private static String appendSlash(String path) {
		if (StringUtils.isEmpty(path)) {
			return "";
		}
		String p = path;
		if (!path.startsWith(SLASH)) {
			p = SLASH + path;
		}
		if(path.endsWith(SLASH)) {
			p = p.substring(0,p.length()-1);
		}
		return p;
	}

	private static String getDefaultCatName() {
		String defaultCat = Objects.requireNonNull(config.getState()).defaultCat;
		return StringUtils.isEmpty(defaultCat) ? "api_generator" : defaultCat;
	}

	private static String getClassCatName(PsiDocComment classDesc) {
		if (classDesc == null) {
			return "";
		}
		return DesUtil.getDescription(classDesc).split(" ")[0];
	}

	private static String getCatId(Map<String, YApiCat> catNameMap, PsiDocComment classDesc) throws IOException {
		String defaultCatName = getDefaultCatName();
		String catName;
		if (Objects.requireNonNull(config.getState()).autoCat) {
			String classCatName = getClassCatName(classDesc);
			catName = StringUtils.isEmpty(classCatName) ? defaultCatName : classCatName;
		} else {
			catName = defaultCatName;
		}
		YApiCat apiCat = catNameMap.get(catName);
		if (apiCat != null) {
			return apiCat.get_id().toString();
		}
		YApiResponse<YApiCat> yApiResponse = YApiSdk.addCategory(config.getState().yApiServerUrl, config.getState().projectToken, config.getState().projectId, catName);
		return yApiResponse.getData().get_id().toString();
	}

	private static Map<String, YApiCat> getCatNameMap() throws IOException {
		List<YApiCat> yApiCats = YApiSdk.listCategories(Objects.requireNonNull(config.getState()).yApiServerUrl, config.getState().projectToken);
		Map<String, YApiCat> catNameMap = new HashMap<>();
		for (YApiCat cat : yApiCats) {
			catNameMap.put(cat.getName(), cat);
		}
		return catNameMap;
	}

	private static List<YApiQuery> listYApiQueries(List<FieldInfo> requestFields, RequestMethodEnum requestMethodEnum) {
		List<YApiQuery> queries = new ArrayList<>();
		for (FieldInfo fieldInfo : requestFields) {
			if (notQuery(fieldInfo.getAnnotations(), requestMethodEnum)) {
				continue;
			}
			if (TypeEnum.LITERAL.equals(fieldInfo.getParamType())) {
				queries.add(buildYApiQuery(fieldInfo));
			} else if (TypeEnum.OBJECT.equals(fieldInfo.getParamType())) {
				List<FieldInfo> children = fieldInfo.getChildren();
				for (FieldInfo info : children) {
					queries.add(buildYApiQuery(info));
				}
			} else {
				YApiQuery apiQuery = buildYApiQuery(fieldInfo);
				apiQuery.setExample("1,1,1");
				queries.add(apiQuery);
			}
		}
		return queries;
	}

	private static boolean notQuery(List<PsiAnnotation> annotations, RequestMethodEnum requestMethodEnum) {
		if (getPathVariableAnnotation(annotations) != null) {
			return true;
		}
		return FieldUtil.findAnnotationByName(annotations, WebAnnotation.RequestBody) != null || !RequestMethodEnum.GET.equals(requestMethodEnum);
	}

	private static YApiQuery buildYApiQuery(FieldInfo fieldInfo) {
		YApiQuery query = new YApiQuery();
		query.setName(fieldInfo.getName());
		query.setDesc(generateDesc(fieldInfo));
		Object value = FieldUtil.getValue(fieldInfo.getPsiType());
		if (value != null) {
			query.setExample(value.toString());
		}
		query.setRequired(convertRequired(fieldInfo.isRequire()));
		return query;
	}

	private static String convertRequired(boolean required) {
		return required ? "1" : "0";
	}

	private static String generateDesc(FieldInfo fieldInfo) {
		if (AssertUtils.isEmpty(fieldInfo.getRange()) || "N/A".equals(fieldInfo.getRange())) {
			return fieldInfo.getDesc();
		}
		if (AssertUtils.isEmpty(fieldInfo.getDesc())) {
			return "值域：" + fieldInfo.getRange();
		}
		return fieldInfo.getDesc() + "，值域：" + fieldInfo.getRange();
	}

	private static List<YApiForm> listYApiForms(List<FieldInfo> requestFields) {
		List<YApiForm> yApiForms = new ArrayList<>();
		for (FieldInfo fieldInfo : requestFields) {
			if (getPathVariableAnnotation(fieldInfo.getAnnotations()) != null) {
				continue;
			}
			if (TypeEnum.LITERAL.equals(fieldInfo.getParamType())) {
				yApiForms.add(buildYApiForm(fieldInfo));
			} else if (TypeEnum.OBJECT.equals(fieldInfo.getParamType())) {
				List<FieldInfo> children = fieldInfo.getChildren();
				for (FieldInfo info : children) {
					yApiForms.add(buildYApiForm(info));
				}
			} else {
				YApiForm apiQuery = buildYApiForm(fieldInfo);
				apiQuery.setExample("1,1,1");
				yApiForms.add(apiQuery);
			}
		}
		return yApiForms;
	}

	private static YApiForm buildYApiForm(FieldInfo fieldInfo) {
		YApiForm param = new YApiForm();
		param.setName(fieldInfo.getName());
		param.setDesc(fieldInfo.getDesc());
		param.setExample(FieldUtil.getValue(fieldInfo.getPsiType()).toString());
		param.setRequired(convertRequired(fieldInfo.isRequire()));
		return param;
	}

	private static RequestMethodEnum getMethodFromAnnotation(PsiAnnotation methodMapping) {
		String text = methodMapping.getText();
		if (text.contains(WebAnnotation.RequestMapping)) {
			return extractMethodFromAttribute(methodMapping);
		}
		return extractMethodFromMappingText(text);
	}

	private static RequestMethodEnum extractMethodFromMappingText(String text) {
		if (text.contains(WebAnnotation.GetMapping)) {
			return RequestMethodEnum.GET;
		}
		if (text.contains(WebAnnotation.PutMapping)) {
			return RequestMethodEnum.PUT;
		}
		if (text.contains(WebAnnotation.DeleteMapping)) {
			return RequestMethodEnum.DELETE;
		}
		if (text.contains(WebAnnotation.PatchMapping)) {
			return RequestMethodEnum.PATCH;
		}
		return RequestMethodEnum.POST;
	}

	private static RequestMethodEnum extractMethodFromAttribute(PsiAnnotation annotation) {
		PsiNameValuePair[] psiNameValuePairs = annotation.getParameterList().getAttributes();
		for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
			if ("method".equals(psiNameValuePair.getName())) {
				return RequestMethodEnum.valueOf(psiNameValuePair.getValue().getReference().resolve().getText());
			}
		}
		return RequestMethodEnum.POST;
	}

	private static PsiAnnotation getMethodMapping(PsiMethod psiMethod) {
		for (PsiAnnotation annotation : psiMethod.getAnnotations()) {
			String text = annotation.getText();
			if (text.contains("Mapping")) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasMappingAnnotation(PsiMethod method) {
		PsiAnnotation[] annotations = method.getAnnotations();
		for (PsiAnnotation annotation : annotations) {
			if (annotation.getText().contains("Mapping")) {
				return true;
			}
		}
		return false;
	}

	public static boolean haveControllerAnnotation(PsiClass psiClass) {
		PsiAnnotation[] annotations = psiClass.getAnnotations();
		for (PsiAnnotation annotation : annotations) {
			if (annotation.getText().contains(WebAnnotation.Controller)) {
				return true;
			}
		}
		return false;
	}


	private static String getDirPath(Project project) {
		String dirPath = config.getState().dirPath;
		if (StringUtils.isEmpty(dirPath)) {
			return project.getBasePath() + "/target/api_docs";
		}

		if (dirPath.endsWith(SLASH)) {
			return dirPath.substring(0, dirPath.lastIndexOf(SLASH));
		}
		return dirPath;
	}

	private static boolean generateDocForClass(Project project, PsiClass psiClass, String dirPath) throws IOException {
		if (!mkDirectory(project, dirPath)) {
			return false;
		}
		String fileName = psiClass.getName();
		File apiDoc = new File(dirPath + SLASH + fileName + ".md");
		boolean notExist = apiDoc.createNewFile();
		if(!notExist) {
			if(!config.getState().overwrite) {
				int choose = Messages.showOkCancelDialog(fileName + ".md already exists,do you want to overwrite it?", "Overwrite Warning!", "Yes", "No", Messages.getWarningIcon());
				if(Messages.CANCEL == choose) {
					return false;
				}
			}
		}
		try (Writer md = new FileWriter(apiDoc)) {
			List<FieldInfo> fieldInfos = listFieldInfos(psiClass);
			md.write("## 示例\n");
			if (AssertUtils.isNotEmpty(fieldInfos)) {
				md.write("```json\n");
				md.write(JsonUtil.buildPrettyJson(fieldInfos) + "\n");
				md.write("```\n");
			}
			md.write("## 参数说明\n");
			if (AssertUtils.isNotEmpty(fieldInfos)) {
				writeParamTableHeader(md);
				for (FieldInfo fieldInfo : fieldInfos) {
					writeFieldInfo(md, fieldInfo);
				}
			}
		}
		return true;
	}

	private static void writeParamTableHeader(Writer md) throws IOException {
		md.write("名称|类型|必填|值域范围|描述/示例\n");
		md.write("---|---|---|---|---\n");
	}

	public static List<FieldInfo> listFieldInfos(PsiClass psiClass) {
		List<FieldInfo> fieldInfos = new ArrayList<>();
		for (PsiField psiField : psiClass.getAllFields()) {
			if (config.getState().excludeFieldNames.contains(psiField.getName())) {
				continue;
			}
			fieldInfos.add(new FieldInfo(psiClass.getProject(), psiField.getName(), psiField.getType(), DesUtil.getDescription(psiField.getDocComment()), psiField.getAnnotations()));
		}
		return fieldInfos;
	}

	private static boolean generateDocForMethod(Project project, PsiMethod selectedMethod, String dirPath) throws IOException {
		if (!mkDirectory(project, dirPath)) {
			return false;
		}
		MethodInfo methodInfo = new MethodInfo(selectedMethod);
		String fileName = getFileName(methodInfo);
		File apiDoc = new File(dirPath + SLASH + fileName + ".md");
		boolean notExist = apiDoc.createNewFile();
		if(!notExist) {
			if(!config.getState().overwrite) {
				int choose = Messages.showOkCancelDialog(fileName + ".md already exists,do you want to overwrite it?", "Overwrite Warning!", "Yes", "No", Messages.getWarningIcon());
				if (Messages.CANCEL == choose) {
					return false;
				}
			}
		}
		Model pomModel = getPomModel(project);
		try (Writer md = new FileWriter(apiDoc)) {
			md.write("## " + fileName + "\n");
			md.write("## 功能介绍\n");
			md.write(methodInfo.getDesc() + "\n");
			md.write("## Maven依赖\n");
			md.write("```xml\n");
			md.write("<dependency>\n");
			md.write("\t<groupId>" + pomModel.getGroupId() + "</groupId>\n");
			md.write("\t<artifactId>" + pomModel.getGroupId() + "</artifactId>\n");
			md.write("\t<version>" + pomModel.getVersion() + "</version>\n");
			md.write("</dependency>\n");
			md.write("```\n");
			md.write("## 接口声明\n");
			md.write("```java\n");
			md.write("package " + methodInfo.getPackageName() + ";\n\n");
			md.write("public interface " + methodInfo.getClassName() + " {\n\n");
			md.write("\t" + methodInfo.getReturnStr() + " " + methodInfo.getMethodName() + methodInfo.getParamStr() + ";\n\n");
			md.write("}\n");
			md.write("```\n");
			md.write("## 请求参数\n");
			md.write("### 请求参数示例\n");
			if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
				md.write("```json\n");
				md.write(JsonUtil.buildPrettyJson(methodInfo.getRequestFields()) + "\n");
				md.write("```\n");
			}
			md.write("### 请求参数说明\n");
			if (AssertUtils.isNotEmpty(methodInfo.getRequestFields())) {
				writeParamTableHeader(md);
				for (FieldInfo fieldInfo : methodInfo.getRequestFields()) {
					writeFieldInfo(md, fieldInfo);
				}
			}
			md.write("\n## 返回结果\n");
			md.write("### 返回结果示例\n");
			if (AssertUtils.isNotEmpty(methodInfo.getResponseFields())) {
				md.write("```json\n");
				md.write(JsonUtil.buildPrettyJson(methodInfo.getResponse()) + "\n");
				md.write("```\n");
			}
			md.write("### 返回结果说明\n");
			if (AssertUtils.isNotEmpty(methodInfo.getResponseFields())) {
				writeParamTableHeader(md);
				for (FieldInfo fieldInfo : methodInfo.getResponseFields()) {
					writeFieldInfo(md, fieldInfo, "");
				}
			}
		}
		return true;
	}

	private static boolean mkDirectory(Project project, String dirPath) {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			boolean success = dir.mkdirs();
			if (!success) {
				NotificationUtil.errorNotify("invalid directory path!", project);
				return false;
			}
		}
		return true;
	}

	private static Model getPomModel(Project project) {
		PsiFile pomFile = FilenameIndex.getFilesByName(project, "pom.xml", GlobalSearchScope.projectScope(project))[0];
		String pomPath = pomFile.getContainingDirectory().getVirtualFile().getPath() + "/pom.xml";
		return readPom(pomPath);
	}

	private static String getFileName(MethodInfo methodInfo) {
		if (!config.getState().cnFileName) {
			return methodInfo.getMethodName();
		}
		if (StringUtils.isEmpty(methodInfo.getDesc()) || !methodInfo.getDesc().contains(" ")) {
			return methodInfo.getMethodName();
		}
		return methodInfo.getDesc().split(" ")[0];
	}

	private static void writeFieldInfo(Writer writer, FieldInfo info) throws IOException {
		writer.write(buildFieldStr(info));
		if (info.hasChildren()) {
			for (FieldInfo fieldInfo : info.getChildren()) {
				writeFieldInfo(writer, fieldInfo, getPrefix());
			}
		}
	}

	private static String buildFieldStr(FieldInfo info) {
		return getFieldName(info) + "|" + info.getPsiType().getPresentableText() + "|" + getRequireStr(info.isRequire()) + "|" + getRange(info.getRange()) + "|" + info.getDesc() + "\n";
	}

	private static String getFieldName(FieldInfo info) {
		if (info.hasChildren()) {
			return "**" + info.getName() + "**";
		}
		return info.getName();
	}

	private static void writeFieldInfo(Writer writer, FieldInfo info, String prefix) throws IOException {
		writer.write(prefix + buildFieldStr(info));
		if (info.hasChildren()) {
			for (FieldInfo fieldInfo : info.getChildren()) {
				writeFieldInfo(writer, fieldInfo, getPrefix() + prefix);
			}
		}
	}

	private static String getPrefix() {
		String prefix = config.getState().prefix;
		if (" ".equals(prefix)) {
			return "&emsp";
		}
		return prefix;
	}

	private static String getRequireStr(boolean isRequire) {
		return isRequire ? "Y" : "N";
	}

	private static String getRange(String range) {
		return AssertUtils.isEmpty(range) ? "N/A" : range;
	}

	public static Model readPom(String pom) {
		MavenXpp3Reader reader = new MavenXpp3Reader();
		try {
			return reader.read(new FileReader(pom));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
