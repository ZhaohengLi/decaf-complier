package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：overriding variable is not allowed for var 'kittyboy'<br>
 * PA2
 */
public class OverridingAbsError extends DecafError {

    private String name;

    @Override
    protected String getErrMsg() {
        return "'" + name + "' is not abstract and does not override all abstract methods";
    }


    public OverridingAbsError(Pos pos, String name) {
        super(pos);
        this.name = name;
        System.out.println("form transform");
    }


}
