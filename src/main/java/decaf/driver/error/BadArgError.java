package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class BadArgError extends DecafError {


  public BadArgError(Pos pos, String method, int exp, int cnt) {
      super(pos);
      this.method = method;
      this.exp = exp;
      this.cnt = cnt;
      this.isLambda = false;
  }
    private String method;
    private int exp;
    private int cnt;
    private boolean isLambda = false;



    @Override
    protected String getErrMsg() {
        if (isLambda)
            return "lambda expression expects " + exp + " argument(s) but " + cnt + " given";
        else
            return "function '" + method + "' expects " + exp + " argument(s) but " + cnt + " given";
    }



    public BadArgError(Pos pos, int exp, int cnt) {
        super(pos);
        this.exp = exp;
        this.cnt = cnt;
        this.isLambda = true;
    }
}
