package decaf.driver.error;

import decaf.frontend.tree.Pos;

public class BadCapValueAssignError extends DecafError {


  @Override
  protected String getErrMsg() {
    return "cannot assign value to captured variables in lambda expression";
  }

  public BadCapValueAssignError(Pos pos) {
    super(pos);
  }

}
