package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class NotCallableError extends DecafError {

    private String type;

    public NotCallableError(Pos pos, String type) {
        super(pos);
        this.type = type;
        System.out.println("form transform");
    }

    @Override
    protected String getErrMsg() {
      System.out.println("form transform");
        return type + " is not a callable type";
    }

}
