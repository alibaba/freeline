package actions;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.psi.*;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;


/**
 * Created by pengwei on 16/9/18.
 */
public class InstallationAction extends BaseAction {
    @Override
    public void actionPerformed() {
        Module[] modules = ModuleManager.getInstance(currentProject).getModules();

        for (Module module : modules) {
            AndroidFacet facet = AndroidFacet.getInstance(module);
            if (facet != null) {
                Manifest manifest = facet.getManifest();
                AndroidAttributeValue<PsiClass> name = manifest.getApplication().getName();
                if (name.getValue() != null) {
                    System.out.println(name.getValue());
                    PsiMethod[] methods = name.getValue().findMethodsByName("onCreate", false);
                    if (methods != null && methods.length > 0) {
                        for (PsiMethod method : methods) {
                            if (method.getParameterList().getParametersCount() == 0) {
                                for (PsiStatement statement : method.getBody().getStatements()) {

                                }
                                break;
                            }
                        }
                    } else {
                        // 插入onCreate方法
                    }
                }
            }
        }

    }
}
