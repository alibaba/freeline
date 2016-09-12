package actions;

/**
 * Created by pengwei on 16/9/11.
 */
public class ForceRunAction extends FreeLineRunAction {
    @Override
    protected String getArgs() {
        return "-f";
    }
}
