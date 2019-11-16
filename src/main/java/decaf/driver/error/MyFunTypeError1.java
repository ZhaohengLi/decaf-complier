package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyFunTypeError1 extends DecafError {

    public MyFunTypeError1(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "arguments in function type must be non-void known type";
    }

}
