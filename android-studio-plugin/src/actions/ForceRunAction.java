package actions;

/**
 * Created by pengwei on 16/9/11.
 */
public class ForceRunAction extends FreelineRunAction {
    @Override
    protected String getArgs() {
        return "-f";
    }
}
