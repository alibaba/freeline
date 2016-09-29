package actions;

/**
 * Created by pengwei on 16/9/11.
 */
public class DebugRunAction extends FreelineRunAction {
    @Override
    protected String getArgs() {
        return "-d";
    }
}
