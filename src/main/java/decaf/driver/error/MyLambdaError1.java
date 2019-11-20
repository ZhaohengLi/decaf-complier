package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyLambdaError1 extends DecafError {

    public MyLambdaError1(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to captured variables in lambda expression";
    }

}
