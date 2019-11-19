package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyLambdaError2 extends DecafError {

    public MyLambdaError2(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "incompatible return types in blocked expression";
    }

}
