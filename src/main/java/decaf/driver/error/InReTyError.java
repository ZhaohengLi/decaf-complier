package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class InReTyError extends DecafError {



    public InReTyError(Pos pos) {
        super(pos);
        System.out.println("form transform");
    }

    @Override
    protected String getErrMsg() {
      System.out.println("form transform");
        return "incompatible return types in blocked expression";
    }

}
