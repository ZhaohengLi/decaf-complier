package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyFunCallError2 extends DecafError {

    private int expect;

    private int count;

    public MyFunCallError2(Pos pos, int expect, int count) {
        super(pos);
        this.expect = expect;
        this.count = count;
    }

    @Override
    protected String getErrMsg() {
        return "lambda expression expects "+ expect + " argument(s) but "+count+" given";
    }

}
