package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class MyFunCallError1 extends DecafError {

    String typeName;

    public MyFunCallError1(Pos pos, String typeName) {
        super(pos);
        this.typeName = typeName;
    }

    @Override
    protected String getErrMsg() {
        return  typeName + " is not a callable type";
    }

}
