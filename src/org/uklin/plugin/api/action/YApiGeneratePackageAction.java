package org.uklin.plugin.api.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.uklin.plugin.api.util.NotificationUtil;
import org.uklin.plugin.api.util.YApiUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YApiGeneratePackageAction extends AnAction {

	private static final VirtualFileSystem localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);

	@Override
	public void actionPerformed(@NotNull AnActionEvent actionEvent) {
		Project project = actionEvent.getDataContext().getData(CommonDataKeys.PROJECT);
		VirtualFile[] files = actionEvent.getDataContext().getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
		if (files == null) {
			return;
		}
		List<PsiFile> psiFiles = new ArrayList<>(64);
		Set<String> fileSet = new HashSet<>(64);
		for (VirtualFile file : files) {
			List<String> temp = genChild(file);
			fileSet.addAll(temp);
		}
		for(String file:fileSet){
			VirtualFile vFile = localFileSystem.findFileByPath(file);
			assert project != null;
			assert vFile != null;
			PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
			psiFiles.add(psiFile);
		}
		YApiUtil.initConfig(project);
		for(PsiFile psiFile:psiFiles){
			String name =psiFile.getName().replace(".java","");
			int index =psiFile.getText().indexOf(name);
			PsiElement referenceAt = psiFile.findElementAt(index);
			PsiClass selectedClass = PsiTreeUtil.getContextOfType(referenceAt, PsiClass.class);
			if (selectedClass == null) {
				NotificationUtil.errorNotify("this operate only support in class file", project);
				return;
			}
			if (YApiUtil.haveControllerAnnotation(selectedClass)) {
				YApiUtil.uploadApiToYApi(project,referenceAt,selectedClass);
				NotificationUtil.infoNotify(name + " api upload success.", project);
			}
		}
	}

	private List<String> genChild(VirtualFile vFile) {
		List<String> result = new ArrayList<>();
		if (vFile.isDirectory()) {
			VirtualFile[] children = vFile.getChildren();
			if (children != null && children.length > 0) {
				for (VirtualFile bean : children) {
					result.addAll(genChild(bean));
				}
			}
		} else if("JAVA".equals(vFile.getFileType().getName())) {
			result.add(vFile.getPath());
		}
		return result;
	}

	@Override
	public boolean isDumbAware() {
		return false;
	}
}
