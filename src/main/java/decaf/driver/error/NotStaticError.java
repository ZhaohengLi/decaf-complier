package decaf.driver.error;

import decaf.frontend.tree.Pos;


public class NotStaticError extends DecafError {

    private String from;
    private String refer;

    public NotStaticError(Pos pos, String from, String refer) {
        super(pos);
        this.from = from;
        this.refer = refer;
    }

    @Override
    protected String getErrMsg() {
        return "can not reference a non-static field '" + refer + "' from static method '" + from + "'";
      
    }

}
