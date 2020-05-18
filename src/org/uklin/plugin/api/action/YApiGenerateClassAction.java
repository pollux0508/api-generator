package org.uklin.plugin.api.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.uklin.plugin.api.util.NotificationUtil;
import org.uklin.plugin.api.util.YApiUtil;

public class YApiGenerateClassAction extends AnAction {
	@Override
	public void actionPerformed(@NotNull AnActionEvent actionEvent) {
		Editor editor = actionEvent.getDataContext().getData(CommonDataKeys.EDITOR);
		if (editor == null) {
			return;
		}
		PsiFile psiFile = actionEvent.getData(CommonDataKeys.PSI_FILE);
		if (psiFile == null) {
			return;
		}
		Project project = editor.getProject();
		if (project == null) {
			return;
		}
		YApiUtil.initConfig(project);
		PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
		PsiClass selectedClass = PsiTreeUtil.getContextOfType(referenceAt, PsiClass.class);
		if (selectedClass == null) {
			NotificationUtil.errorNotify("this operate only support in class file", project);
			return;
		}
		if (selectedClass.isInterface()) {
			YApiUtil.generateMarkdownForInterface(project, referenceAt, selectedClass);
			return;
		}
		if (YApiUtil.haveControllerAnnotation(selectedClass)) {
			YApiUtil.uploadApiToYApi(project, referenceAt, selectedClass);
			return;
		}
		YApiUtil.generateMarkdownForClass(project, selectedClass);
	}

	@Override
	public boolean isDumbAware() {
		return false;
	}
}
