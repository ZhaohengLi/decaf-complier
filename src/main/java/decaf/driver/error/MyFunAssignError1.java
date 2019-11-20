package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyFunAssignError1 extends DecafError {

    String name;

    public MyFunAssignError1(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to class member method '" + name + "'";
    }

}
