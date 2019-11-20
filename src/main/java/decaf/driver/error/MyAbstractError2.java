package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyAbstractError2 extends DecafError {

    String className;

    public MyAbstractError2(Pos pos, String className) {
        super(pos);
        this.className = className;
    }

    @Override
    protected String getErrMsg() {
        return "cannot instantiate abstract class '" + className + "'";
    }

}
