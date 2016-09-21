package actions;

/**
 * Created by pengwei on 16/9/11.
 */
public class DebugRunAction extends FreeLineRunAction {
    @Override
    protected String getArgs() {
        return "-d";
    }
}
